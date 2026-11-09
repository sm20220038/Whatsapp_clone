-module(whatsapp_web).
-export([start/0, start/1]).

-define(DEFAULT_PORT, 8081).
-define(TLS_PORT, 8443).
-define(CERT_FILE, "certs/dev.crt").
-define(KEY_FILE, "certs/dev.key").

-define(JWT_SECRET, list_to_binary(os:getenv("WACLONE_JWT_SECRET", "waclone-shared-secret-change-me-please-0123456789"))).
-define(WS_MAGIC, "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").

start() -> start(?DEFAULT_PORT).

start(Port) ->
    whatsapp_server:start_registry(),
    io:format("=============================================~n"),
    io:format(" WhatsApp WebSocket server on ws://localhost:~p/~n", [Port]),
    case filelib:is_file(?CERT_FILE) andalso filelib:is_file(?KEY_FILE) of
        true ->
            {ok, _} = application:ensure_all_started(ssl),
            spawn(fun() -> start_tls(?TLS_PORT) end),
            io:format("                     and on wss://localhost:~p/~n", [?TLS_PORT]);
        false ->
            io:format(" (no certs/ found - wss disabled, run tools/make-cert.mjs)~n")
    end,
    io:format("=============================================~n"),
    {ok, L} = gen_tcp:listen(Port, [binary, {packet, line},
                                    {active, false}, {reuseaddr, true}]),
    accept(L).

accept(L) ->
    {ok, S} = gen_tcp:accept(L),
    Pid = spawn(fun() ->
                        receive go -> ok end,
                        init_conn({gen_tcp, S})
                end),
    ok = gen_tcp:controlling_process(S, Pid),
    Pid ! go,
    accept(L).

start_tls(Port) ->
    {ok, L} = ssl:listen(Port, [binary, {packet, line},
                                {active, false}, {reuseaddr, true},
                                {certfile, ?CERT_FILE}, {keyfile, ?KEY_FILE}]),
    accept_tls(L).

accept_tls(L) ->
    {ok, S0} = ssl:transport_accept(L),

    Pid = spawn(fun() ->
                        receive go -> ok end,
                        case ssl:handshake(S0, 10000) of
                            {ok, S} -> init_conn({ssl, S});
                            _ -> ssl:close(S0)
                        end
                end),
    ok = ssl:controlling_process(S0, Pid),
    Pid ! go,
    accept_tls(L).

init_conn(T) ->
    case handshake(T) of
        ok ->
            t_setopts(T, [{packet, raw}, {active, true}]),
            loop(T, {undefined, undefined}, <<>>);
        error ->
            t_close(T)
    end.

t_send({Mod, S}, Data)         -> Mod:send(S, Data).
t_recv({Mod, S}, Len, Timeout) -> Mod:recv(S, Len, Timeout).
t_close({Mod, S})              -> Mod:close(S).
t_setopts({gen_tcp, S}, Opts)  -> inet:setopts(S, Opts);
t_setopts({ssl, S}, Opts)      -> ssl:setopts(S, Opts).

handshake(T) ->
    case read_headers(T, undefined) of
        undefined -> error;
        Key ->
            Accept = base64:encode(crypto:hash(sha, <<Key/binary, ?WS_MAGIC>>)),
            t_send(T, [<<"HTTP/1.1 101 Switching Protocols\r\n">>,
                       <<"Upgrade: websocket\r\n">>,
                       <<"Connection: Upgrade\r\n">>,
                       <<"Sec-WebSocket-Accept: ">>, Accept, <<"\r\n\r\n">>]),
            ok
    end.

read_headers(T, KeyAcc) ->
    case t_recv(T, 0, 10000) of
        {ok, Line} ->
            case chomp(Line) of
                <<>> -> KeyAcc;
                Header ->
                    case binary:split(Header, <<":">>) of
                        [Name, Val] ->
                            case iolist_to_binary(string:lowercase(string:trim(Name))) of
                                <<"sec-websocket-key">> ->
                                    read_headers(T, iolist_to_binary(string:trim(Val)));
                                _ -> read_headers(T, KeyAcc)
                            end;
                        _ -> read_headers(T, KeyAcc)
                    end
            end;
        _ -> undefined
    end.

chomp(<<>>) -> <<>>;
chomp(Bin) ->
    Sz = byte_size(Bin) - 1,
    case Bin of
        <<Head:Sz/binary, C>> when C =:= $\n; C =:= $\r -> chomp(Head);
        _ -> Bin
    end.

loop(T = {_, Sock}, State = {User, Pending}, Buf) ->
    receive
        {Tag, Sock, Data} when Tag =:= tcp; Tag =:= ssl ->
            case feed(T, <<Buf/binary, Data/binary>>, State) of
                {closed, {U, _}}   -> logout(U);
                {Buf2, State2}     -> loop(T, State2, Buf2)
            end;
        {tcp_closed, Sock} -> logout(User);
        {ssl_closed, Sock} -> logout(User);
        {tcp_error, Sock, _} -> logout(User);
        {ssl_error, Sock, _} -> logout(User);
        {push, Frame} ->
            send_json(T, Frame),
            loop(T, State, Buf);
        session_replaced ->

            t_close(T),
            io:format("WS replaced: ~s~n", [User]);
        {login_result, ok, Users, Groups, Mutes} ->
            send_json(T, #{<<"type">> => <<"login_ok">>, <<"user">> => Pending,
                           <<"users">> => Users, <<"groups">> => Groups,
                           <<"mutes">> => Mutes}),
            io:format("WS login: ~s~n", [Pending]),
            loop(T, {Pending, Pending}, Buf)
    end.

logout(undefined) -> ok;
logout(User) ->
    registry ! {logout, User, self()},
    io:format("WS offline: ~s~n", [User]).

feed(T, Buf, State) ->
    case decode_frame(Buf) of
        {ok, Opcode, Payload, Rest} ->
            case ws_op(T, Opcode, Payload, State) of
                close -> t_close(T), {closed, State};
                State2 -> feed(T, Rest, State2)
            end;
        more ->
            {Buf, State}
    end.

ws_op(T, 8, _Payload, _State) -> t_send(T, <<136, 0>>), close;
ws_op(T, 9, Payload, State) ->
    t_send(T, ws_frame(10, Payload)), State;
ws_op(_T, 10, _Payload, State) -> State;
ws_op(T, 1, Payload, State) ->
    case decode_json(Payload) of
        {ok, Frame} -> handle_text(T, State, Frame);
        error -> send_json(T, err(<<"bad json">>)), State
    end;
ws_op(_T, _Op, _Payload, State) -> State.

handle_text(T, State = {User, _Pending}, Frame) ->
    case Frame of
        #{<<"type">> := <<"login">>} ->
            case verify_login(Frame) of
                {ok, U} ->
                    registry ! {login, U, self()},
                    {User, U};
                error ->
                    send_json(T, err(<<"invalid token">>)),
                    State
            end;
        _ when User =:= undefined ->
            send_json(T, err(<<"login first">>)),
            State;
        _ ->
            dispatch(T, User, Frame),
            State
    end.

dispatch(T, User, F = #{<<"type">> := Type}) ->
    case route(User, F, Type) of
        {send, Msg}   -> registry ! Msg, ok;
        ok            -> ok;
        {error, Why}  -> send_json(T, err(Why))
    end;
dispatch(T, _User, _F) ->
    send_json(T, err(<<"missing type">>)).

route(User, F, <<"msg">>) ->
    need(F, [<<"to">>, <<"id">>, <<"text">>],
         fun([To, Id, Text]) ->
                 {send, {direct, User, To, Id, Text, whatsapp_server:reply_of(F)}}
         end);
route(User, F, <<"gmsg">>) ->
    need(F, [<<"group">>, <<"id">>, <<"text">>],
         fun([G, Id, Text]) ->
                 {send, {gmsg, User, G, Id, Text, whatsapp_server:reply_of(F)}}
         end);
route(User, F, <<"react">>) ->
    need(F, [<<"target">>, <<"emoji">>],
         fun(_) ->
                 case F of
                     #{<<"group">> := G} -> {send, {react_g, User, G, F}};
                     #{<<"to">> := To}   -> {send, {react_d, User, To, F}};
                     _ -> {error, <<"react needs to/group">>}
                 end
         end);
route(User, F, <<"msg_edit">>) ->
    need(F, [<<"target">>, <<"text">>], fun(_) -> amend(User, F, <<"msg_edit">>) end);
route(User, F, <<"msg_delete">>) ->
    need(F, [<<"target">>], fun(_) -> amend(User, F, <<"msg_delete">>) end);
route(User, F, <<"create_group">>) ->
    need(F, [<<"name">>, <<"members">>],
         fun([Name, Members]) when is_list(Members) ->
                 {send, {create_group, User, Name, Members}};
            (_) -> {error, <<"members must be a list">>}
         end);
route(User, F, <<"add_members">>) ->
    need(F, [<<"group">>, <<"members">>],
         fun([G, Members]) when is_list(Members) ->
                 {send, {add_members, User, G, Members}};
            (_) -> {error, <<"members must be a list">>}
         end);
route(User, F, <<"set_admin">>) ->
    need(F, [<<"group">>, <<"user">>],
         fun([G, Target]) ->
                 {send, {set_admin, User, G, Target, maps:get(<<"admin">>, F, true)}}
         end);
route(User, F, <<"remove_member">>) ->
    need(F, [<<"group">>, <<"user">>],
         fun([G, Target]) -> {send, {remove_member, User, G, Target}} end);
route(User, F, <<"leave_group">>) ->
    need(F, [<<"group">>],
         fun([G]) -> {send, {leave_group, User, G}} end);
route(User, F, <<"ack_delivered">>) -> ack(User, F, <<"delivered">>);
route(User, F, <<"ack_read">>)      -> ack(User, F, <<"read">>);
route(User, F, <<"typing">>) ->
    case F of
        #{<<"group">> := G} -> {send, {typing_g, User, G}};
        #{<<"to">> := To}   -> {send, {typing_d, User, To}};
        _ -> {error, <<"typing needs to/group">>}
    end;
route(User, F, <<"media">>) ->
    need(F, [<<"id">>],
         fun(_) ->
                 case F of
                     #{<<"group">> := G} -> {send, {media_group, User, G, F}};
                     #{<<"to">> := To}   -> {send, {media_direct, User, To, F}};
                     _ -> {error, <<"media needs to/group">>}
                 end
         end);
route(User, F, <<"mute">>)   -> mute(User, F, true);
route(User, F, <<"unmute">>) -> mute(User, F, false);

route(User, F, <<"call_offer">>) ->
    need(F, [<<"to">>, <<"sdp">>],
         fun([To, Sdp]) ->
                 {send, {call, User, To, <<"call_offer">>,
                         #{<<"sdp">> => Sdp,
                           <<"video">> => maps:get(<<"video">>, F, false)}}}
         end);
route(User, F, <<"call_answer">>) ->
    need(F, [<<"to">>, <<"sdp">>],
         fun([To, Sdp]) ->
                 {send, {call, User, To, <<"call_answer">>, #{<<"sdp">> => Sdp}}}
         end);
route(User, F, <<"call_ice">>) ->
    need(F, [<<"to">>, <<"candidate">>],
         fun([To, Candidate]) ->
                 {send, {call, User, To, <<"call_ice">>, #{<<"candidate">> => Candidate}}}
         end);
route(User, F, <<"call_reject">>) ->
    need(F, [<<"to">>],
         fun([To]) ->
                 {send, {call, User, To, <<"call_reject">>,
                         #{<<"reason">> => maps:get(<<"reason">>, F, <<"declined">>)}}}
         end);
route(User, F, <<"call_end">>) ->
    need(F, [<<"to">>],
         fun([To]) -> {send, {call, User, To, <<"call_end">>, #{}}} end);

route(_User, _F, _Type) -> {error, <<"unknown type">>}.

ack(User, F, Status) ->
    need(F, [<<"to">>, <<"id">>],
         fun([To, Id]) -> {send, {ack, User, To, Id, Status}} end).

amend(User, F, Type) ->
    case F of
        #{<<"group">> := G} -> {send, {msg_amend_g, User, G, Type, F}};
        #{<<"to">> := To}   -> {send, {msg_amend, User, To, Type, F}};
        _ -> {error, <<Type/binary, " needs to/group">>}
    end.

mute(User, F, On) ->
    need(F, [<<"user">>],
         fun([Target]) -> {send, {mute, User, Target, On, self()}} end).

need(F, Keys, Fun) ->
    case [K || K <- Keys, not maps:is_key(K, F)] of
        []      -> Fun([maps:get(K, F) || K <- Keys]);
        [K | _] -> {error, <<"missing field: ", K/binary>>}
    end.

verify_login(#{<<"token">> := Token}) when is_binary(Token) ->
    case binary:split(Token, <<".">>, [global]) of
        [H, P, Sig] ->
            Signing = <<H/binary, ".", P/binary>>,
            Expected = b64url_encode(crypto:mac(hmac, sha256, ?JWT_SECRET, Signing)),
            case Expected =:= Sig of
                true ->
                    try
                        Claims = json:decode(b64url_decode(P)),
                        Now = erlang:system_time(second),
                        Exp = maps:get(<<"exp">>, Claims, 0),
                        case is_integer(Exp) andalso Exp > Now of
                            true  -> {ok, maps:get(<<"sub">>, Claims)};
                            false -> error
                        end
                    catch _:_ -> error end;
                false -> error
            end;
        _ -> error
    end;
verify_login(#{<<"user">> := U}) when is_binary(U), byte_size(U) > 0 ->
    {ok, U};
verify_login(_) -> error.

decode_frame(<<_Fin:1, _Rsv:3, Opcode:4, Mask:1, Len0:7, R/binary>>) ->
    case ext_len(Len0, R) of
        {Len, R1} ->
            case Mask of
                1 ->
                    case R1 of
                        <<MaskKey:4/binary, R2/binary>> when byte_size(R2) >= Len ->
                            <<Masked:Len/binary, Rest/binary>> = R2,
                            {ok, Opcode, unmask(Masked, MaskKey), Rest};
                        _ -> more
                    end;
                0 ->
                    case R1 of
                        <<Payload:Len/binary, Rest/binary>> -> {ok, Opcode, Payload, Rest};
                        _ -> more
                    end
            end;
        more -> more;
        _ -> more
    end;
decode_frame(_) -> more.

ext_len(126, <<Len:16, R/binary>>) -> {Len, R};
ext_len(126, _) -> more;
ext_len(127, <<Len:64, R/binary>>) -> {Len, R};
ext_len(127, _) -> more;
ext_len(Len, R) when Len =< 125 -> {Len, R}.

unmask(Bin, Key) -> unmask(Bin, Key, 0, <<>>).
unmask(<<>>, _Key, _I, Acc) -> Acc;
unmask(<<B, Rest/binary>>, Key, I, Acc) ->
    M = binary:at(Key, I rem 4),
    unmask(Rest, Key, I + 1, <<Acc/binary, (B bxor M)>>).

ws_frame(Opcode, Payload) ->
    Len = byte_size(Payload),
    Header = if
        Len < 126     -> <<1:1, 0:3, Opcode:4, 0:1, Len:7>>;
        Len < 65536   -> <<1:1, 0:3, Opcode:4, 0:1, 126:7, Len:16>>;
        true          -> <<1:1, 0:3, Opcode:4, 0:1, 127:7, Len:64>>
    end,
    <<Header/binary, Payload/binary>>.

send_json(T, Map) ->
    t_send(T, ws_frame(1, iolist_to_binary(json:encode(Map)))).

decode_json(Bin) ->
    try {ok, json:decode(Bin)} catch _:_ -> error end.

err(Reason) -> #{<<"type">> => <<"error">>, <<"reason">> => Reason}.

b64url_encode(Bin) ->
    << <<(url_c(C))>> || <<C>> <= base64:encode(Bin), C =/= $= >>.
url_c($+) -> $-;
url_c($/) -> $_;
url_c(C)  -> C.

b64url_decode(Bin) ->
    Std = << <<(std_c(C))>> || <<C>> <= Bin >>,
    base64:decode(pad(Std)).
std_c($-) -> $+;
std_c($_) -> $/;
std_c(C)  -> C.
pad(B) ->
    case byte_size(B) rem 4 of
        0 -> B;
        2 -> <<B/binary, "==">>;
        3 -> <<B/binary, "=">>;
        _ -> B
    end.
