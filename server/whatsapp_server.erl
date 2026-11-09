-module(whatsapp_server).
-export([start/0, start/1, start_registry/0, reply_of/1]).

-define(DEFAULT_PORT, 5555).

-define(BACKEND_URL, "http://localhost:8080").
-define(INTERNAL_SECRET, os:getenv("WACLONE_JWT_SECRET", "waclone-shared-secret-change-me-please-0123456789")).

start_registry() ->
    case whereis(registry) of
        undefined -> spawn_registry();
        Pid -> Pid
    end.

spawn_registry() ->
    S0 = #{users => #{},
           online => #{},
           queues => #{},
           groups => #{},
           mutes  => #{},
           keys   => #{},

           gpfx   => integer_to_list(erlang:system_time(second)),
           gseq   => 1},
    Pid = spawn(fun() -> registry(S0) end),
    register(registry, Pid),
    spawn(fun() -> keeper(Pid) end),
    spawn(fun() -> restore_groups(20) end),
    Pid.

restore_groups(0) ->
    io:format("Group restore: backend not reachable, skipping~n");
restore_groups(Retries) ->
    application:ensure_all_started(inets),
    Req = {?BACKEND_URL ++ "/api/internal/groups",
           [{"x-internal-auth", ?INTERNAL_SECRET}]},
    case httpc:request(get, Req, [{timeout, 4000}], [{body_format, binary}]) of
        {ok, {{_, 200, _}, _, Body}} ->
            try
                Groups = [{maps:get(<<"id">>, G),
                           #{name    => maps:get(<<"name">>, G),
                             members => maps:get(<<"members">>, G),
                             admins  => maps:get(<<"admins">>, G, []),
                             owner   => maps:get(<<"owner">>, G, undefined)}}
                          || G <- json:decode(Body)],
                registry ! {restore_groups, Groups}
            catch _:_ ->
                io:format("Group restore: bad response, skipping~n")
            end;
        _ ->
            timer:sleep(3000),
            restore_groups(Retries - 1)
    end.

keeper(Pid) ->
    Ref = erlang:monitor(process, Pid),
    receive
        {'DOWN', Ref, process, Pid, normal} -> ok;
        {'DOWN', Ref, process, Pid, Reason} ->
            io:format("!! registry died (~p) - restarting~n", [Reason]),
            timer:sleep(100),
            case whereis(registry) of
                undefined -> spawn_registry();
                _ -> ok
            end
    end.

start() -> start(?DEFAULT_PORT).

start(Port) ->
    start_registry(),
    {ok, Listen} = gen_tcp:listen(Port, [binary,
                                          {packet, line},
                                          {active, false},
                                          {reuseaddr, true}]),

    spawn(fun() -> start_media(Port + 1) end),
    io:format("==============================================~n"),
    io:format(" WhatsApp server v2 (JSON) on port ~p~n", [Port]),
    io:format(" Media/blob service on port ~p~n", [Port + 1]),
    io:format("==============================================~n"),
    accept_loop(Listen).

start_media(Port) ->
    filelib:ensure_dir(filename:join("media", "x")),
    {ok, L} = gen_tcp:listen(Port, [binary, {packet, line},
                                    {active, false}, {reuseaddr, true}]),
    media_accept(L).

media_accept(L) ->
    {ok, S} = gen_tcp:accept(L),
    spawn(fun() -> media_handle(S) end),
    media_accept(L).

-define(MEDIA_CHUNK, 262144).

media_handle(S) ->
    case gen_tcp:recv(S, 0) of
        {ok, Line} ->
            case binary:split(strip(Line), <<"|">>, [global]) of
                [<<"PUT">>, Id, SizeB] ->
                    Size = binary_to_integer(SizeB),
                    inet:setopts(S, [{packet, raw}]),

                    case file:open(media_path(Id), [write, raw, binary]) of
                        {ok, IoDev} ->
                            Res = recv_to_file(S, IoDev, Size),
                            file:close(IoDev),
                            case Res of
                                ok ->
                                    io:format("Media PUT ~s (~p bytes)~n", [Id, Size]),
                                    gen_tcp:send(S, [<<"OK|">>, Id, <<"\n">>]);
                                _ ->
                                    file:delete(media_path(Id)),
                                    gen_tcp:send(S, <<"ERR|incomplete\n">>)
                            end;
                        _ ->
                            gen_tcp:send(S, <<"ERR|storage\n">>)
                    end;
                [<<"GET">>, Id] ->
                    Path = media_path(Id),
                    case filelib:is_regular(Path) of
                        true ->
                            Size = filelib:file_size(Path),
                            gen_tcp:send(S, [<<"SIZE|">>, integer_to_binary(Size), <<"\n">>]),

                            {ok, IoDev} = file:open(Path, [read, raw, binary]),
                            send_from_file(S, IoDev),
                            file:close(IoDev);
                        false ->
                            gen_tcp:send(S, <<"ERR|notfound\n">>)
                    end;
                _ ->
                    gen_tcp:send(S, <<"ERR|badcmd\n">>)
            end,
            gen_tcp:close(S);
        _ ->
            gen_tcp:close(S)
    end.

recv_to_file(_S, _IoDev, 0) -> ok;
recv_to_file(S, IoDev, Remaining) ->
    Want = min(?MEDIA_CHUNK, Remaining),
    case gen_tcp:recv(S, Want, 120000) of
        {ok, Data} ->
            case file:write(IoDev, Data) of
                ok -> recv_to_file(S, IoDev, Remaining - byte_size(Data));
                Err -> Err
            end;
        Err -> Err
    end.

send_from_file(S, IoDev) ->
    case file:read(IoDev, ?MEDIA_CHUNK) of
        {ok, Data} ->
            case gen_tcp:send(S, Data) of
                ok -> send_from_file(S, IoDev);
                _ -> ok
            end;
        eof -> ok;
        _ -> ok
    end.

media_path(Id) ->

    Safe = << <<C>> || <<C>> <= Id, (C >= $a andalso C =< $z)
                                 orelse (C >= $A andalso C =< $Z)
                                 orelse (C >= $0 andalso C =< $9)
                                 orelse C =:= $- >>,
    filename:join("media", binary_to_list(Safe)).

accept_loop(Listen) ->
    {ok, Socket} = gen_tcp:accept(Listen),
    Pid = spawn(fun() -> conn_init(Socket) end),

    ok = gen_tcp:controlling_process(Socket, Pid),
    Pid ! go,
    accept_loop(Listen).

conn_init(Socket) ->
    receive go -> ok end,
    inet:setopts(Socket, [{active, true}]),
    conn(Socket, undefined).

conn(Socket, User) ->
    receive
        {tcp, Socket, Line} ->
            User2 =
                case decode(Line) of
                    {ok, Frame} ->
                        try handle(Socket, User, Frame)
                        catch _:_ ->
                            reply(Socket, err(<<"malformed frame">>)),
                            User
                        end;
                    error ->
                        reply(Socket, err(<<"bad json">>)),
                        User
                end,
            conn(Socket, User2);
        {tcp_closed, Socket} -> offline(User);
        {tcp_error, Socket, _} -> offline(User);
        {push, Frame} ->
            reply(Socket, Frame),
            conn(Socket, User);
        session_replaced ->

            gen_tcp:close(Socket),
            io:format("Replaced: ~s~n", [User])
    end.

offline(undefined) -> ok;
offline(User) ->
    registry ! {logout, User, self()},
    io:format("Offline: ~s~n", [User]).

handle(Socket, undefined, #{<<"type">> := <<"login">>, <<"user">> := U})
  when is_binary(U), byte_size(U) > 0 ->
    registry ! {login, U, self()},
    receive
        {login_result, ok, Users, Groups, Mutes} ->
            reply(Socket, #{<<"type">> => <<"login_ok">>, <<"user">> => U,
                            <<"users">> => Users, <<"groups">> => Groups,
                            <<"mutes">> => Mutes}),
            io:format("Login: ~s~n", [U]),
            U
    after 5000 ->
            reply(Socket, err(<<"registry timeout">>)),
            undefined
    end;

handle(Socket, undefined, _) ->
    reply(Socket, err(<<"login first">>)),
    undefined;

handle(Socket, User, F = #{<<"type">> := Type}) ->
    case Type of
        <<"msg">> ->
            registry ! {direct, User, maps:get(<<"to">>, F),
                        maps:get(<<"id">>, F), maps:get(<<"text">>, F), reply_of(F)};
        <<"gmsg">> ->
            registry ! {gmsg, User, maps:get(<<"group">>, F),
                        maps:get(<<"id">>, F), maps:get(<<"text">>, F), reply_of(F)};
        <<"react">> ->
            case F of
                #{<<"group">> := G} -> registry ! {react_g, User, G, F};
                #{<<"to">> := To}   -> registry ! {react_d, User, To, F};
                _ -> ok
            end;
        <<"msg_edit">> ->
            case F of
                #{<<"group">> := G} -> registry ! {msg_amend_g, User, G, <<"msg_edit">>, F};
                #{<<"to">> := To}   -> registry ! {msg_amend, User, To, <<"msg_edit">>, F};
                _ -> ok
            end;
        <<"msg_delete">> ->
            case F of
                #{<<"group">> := G} -> registry ! {msg_amend_g, User, G, <<"msg_delete">>, F};
                #{<<"to">> := To}   -> registry ! {msg_amend, User, To, <<"msg_delete">>, F};
                _ -> ok
            end;
        <<"create_group">> ->
            registry ! {create_group, User, maps:get(<<"name">>, F),
                        maps:get(<<"members">>, F)};
        <<"add_members">> ->
            registry ! {add_members, User, maps:get(<<"group">>, F),
                        maps:get(<<"members">>, F)};
        <<"set_admin">> ->
            registry ! {set_admin, User, maps:get(<<"group">>, F),
                        maps:get(<<"user">>, F), maps:get(<<"admin">>, F, true)};
        <<"remove_member">> ->
            registry ! {remove_member, User, maps:get(<<"group">>, F),
                        maps:get(<<"user">>, F)};
        <<"leave_group">> ->
            registry ! {leave_group, User, maps:get(<<"group">>, F)};
        <<"ack_delivered">> ->
            registry ! {ack, User, maps:get(<<"to">>, F),
                        maps:get(<<"id">>, F), <<"delivered">>};
        <<"ack_read">> ->
            registry ! {ack, User, maps:get(<<"to">>, F),
                        maps:get(<<"id">>, F), <<"read">>};
        <<"typing">> ->
            case F of
                #{<<"group">> := G} -> registry ! {typing_g, User, G};
                #{<<"to">> := To}   -> registry ! {typing_d, User, To};
                _ -> ok
            end;
        <<"call_invite">> ->
            registry ! {call, User, maps:get(<<"to">>, F), <<"call_invite">>,
                        call_extra(Socket, F)};
        <<"call_accept">> ->
            registry ! {call, User, maps:get(<<"to">>, F), <<"call_accept">>,
                        call_extra(Socket, F)};
        <<"call_reject">> ->
            registry ! {call, User, maps:get(<<"to">>, F), <<"call_reject">>,
                        #{<<"reason">> => maps:get(<<"reason">>, F, <<"declined">>)}};
        <<"call_end">> ->
            registry ! {call, User, maps:get(<<"to">>, F), <<"call_end">>, #{}};
        <<"media">> ->
            case F of
                #{<<"group">> := G} -> registry ! {media_group, User, G, F};
                #{<<"to">> := To}   -> registry ! {media_direct, User, To, F};
                _ -> reply(Socket, err(<<"media needs to/group">>))
            end;
        <<"register_keys">> ->
            registry ! {register_keys, User, maps:get(<<"bundle">>, F)};
        <<"fetch_bundle">> ->
            registry ! {fetch_bundle, User, maps:get(<<"user">>, F), self()};
        <<"emsg">> ->
            registry ! {emsg, User, maps:get(<<"to">>, F), F};
        <<"rekey">> ->
            registry ! {rekey, User, maps:get(<<"to">>, F)};
        <<"mute">> ->
            registry ! {mute, User, maps:get(<<"user">>, F), true, self()};
        <<"unmute">> ->
            registry ! {mute, User, maps:get(<<"user">>, F), false, self()};
        _ ->
            reply(Socket, err(<<"unknown type">>))
    end,
    User.

registry(S = #{users := Users, online := Online, queues := Qs,
               groups := Gs, mutes := Mutes, keys := Keys}) ->
    receive
        {register_keys, User, Bundle} ->
            io:format("Keys registered: ~s~n", [User]),
            registry(S#{keys := Keys#{User => Bundle}});

        {fetch_bundle, _Req, Target, Pid} ->
            case maps:find(Target, Keys) of
                {ok, B} ->

                    {OpkFields, Rest} =
                        case maps:get(<<"opks">>, B, []) of
                            [O | T] -> {#{<<"opk_id">> => maps:get(<<"id">>, O),
                                          <<"opk">> => maps:get(<<"pub">>, O)}, T};
                            [] -> {#{<<"opk_id">> => -1}, []}
                        end,
                    Bundle = maps:merge(
                               #{<<"type">> => <<"bundle">>, <<"user">> => Target,
                                 <<"present">> => true,
                                 <<"ik_dh">> => maps:get(<<"ik_dh">>, B),
                                 <<"ik_sig">> => maps:get(<<"ik_sig">>, B),
                                 <<"spk">> => maps:get(<<"spk">>, B),
                                 <<"spk_sig">> => maps:get(<<"spk_sig">>, B)},
                               OpkFields),
                    Pid ! {push, Bundle},
                    registry(S#{keys := Keys#{Target => B#{<<"opks">> => Rest}}});
                error ->
                    Pid ! {push, #{<<"type">> => <<"bundle">>,
                                   <<"user">> => Target, <<"present">> => false}},
                    registry(S)
            end;

        {emsg, From, To, F} ->
            case maps:is_key(To, Users) of
                false ->
                    send_online(From, err(<<"unknown user">>), Online),
                    registry(S);
                true ->
                    Id = maps:get(<<"id">>, F),
                    send_online(From, receipt(Id, <<"sent">>, To), Online),
                    case is_muted(To, From, Mutes) of
                        true ->
                            registry(S);
                        false ->
                            Frame = #{<<"type">> => <<"emsg">>, <<"id">> => Id,
                                      <<"from">> => From,
                                      <<"env">> => maps:get(<<"env">>, F),
                                      <<"ts">> => erlang:system_time(millisecond)},
                            io:format("Emsg: ~s -> ~s~n", [From, To]),
                            registry(send_or_queue(To, Frame, S))
                    end
            end;

        {rekey, From, To} ->
            send_online(To, #{<<"type">> => <<"rekey">>, <<"from">> => From}, Online),
            registry(S);
        {login, U, Pid} ->

            case maps:find(U, Online) of
                {ok, Old} when Old =/= Pid ->
                    Old ! {push, #{<<"type">> => <<"session_replaced">>}},
                    Old ! session_replaced;
                _ -> ok
            end,
            UsersList =
                [#{<<"user">> => Uu,
                   <<"online">> => maps:is_key(Uu, Online),
                   <<"last_seen">> => LS}
                 || {Uu, LS} <- maps:to_list(Users), Uu =/= U],
            GroupsList =
                [group_view(Gid, G)
                 || {Gid, G} <- maps:to_list(Gs),
                    lists:member(U, maps:get(members, G))],
            MyMutes = maps:keys(maps:get(U, Mutes, #{})),
            Pid ! {login_result, ok, UsersList, GroupsList, MyMutes},
            [Pid ! {push, Fr} || Fr <- lists:reverse(maps:get(U, Qs, []))],
            Online2 = Online#{U => Pid},
            broadcast_presence(#{<<"type">> => <<"presence">>, <<"user">> => U,
                                 <<"online">> => true, <<"last_seen">> => 0},
                               U, Online2, Mutes),
            registry(S#{users := Users#{U => maps:get(U, Users, 0)},
                        online := Online2,
                        queues := maps:remove(U, Qs)});

        {logout, U, Pid} ->
            case maps:find(U, Online) of
                {ok, Pid} ->
                    Now = erlang:system_time(millisecond),
                    Online2 = maps:remove(U, Online),
                    broadcast_presence(#{<<"type">> => <<"presence">>, <<"user">> => U,
                                         <<"online">> => false, <<"last_seen">> => Now},
                                       U, Online2, Mutes),
                    registry(S#{online := Online2, users := Users#{U => Now}});
                _ ->
                    registry(S)
            end;

        {mute, Muter, Target, On, Pid} ->
            MySet = maps:get(Muter, Mutes, #{}),
            MySet2 = case On of
                         true  -> MySet#{Target => true};
                         false -> maps:remove(Target, MySet)
                     end,
            Pid ! {push, #{<<"type">> => <<"mute_ok">>,
                           <<"user">> => Target, <<"muted">> => On}},
            io:format("Mute ~s: ~s -> ~s~n", [On, Muter, Target]),
            registry(S#{mutes := Mutes#{Muter => MySet2}});

        {direct, From, To, Id, Text, Reply} ->
            case maps:is_key(To, Users) of
                false ->
                    send_online(From, err(<<"unknown user: ", To/binary>>), Online),
                    registry(S);
                true ->
                    Ts = erlang:system_time(millisecond),
                    send_online(From, receipt(Id, <<"sent">>, To), Online),

                    case is_muted(To, From, Mutes) of
                        true ->
                            io:format("Msg (muted, dropped): ~s -> ~s~n", [From, To]),
                            registry(S);
                        false ->
                            Frame = maps:merge(Reply,
                                      #{<<"type">> => <<"msg">>, <<"id">> => Id,
                                        <<"from">> => From, <<"text">> => Text,
                                        <<"ts">> => Ts}),
                            io:format("Msg: ~s -> ~s~n", [From, To]),
                            registry(send_or_queue(To, Frame, S))
                    end
            end;

        {ack, Acker, To, Id, Status} ->
            registry(send_or_queue(To, receipt(Id, Status, Acker), S));

        {react_d, From, To, F} ->
            case is_muted(To, From, Mutes) of
                true -> registry(S);
                false -> registry(send_or_queue(To, reaction_frame(From, F, undefined), S))
            end;

        {react_g, From, Gid, F} ->
            case maps:get(Gid, Gs, undefined) of
                #{members := Members} ->
                    Frame = reaction_frame(From, F, Gid),
                    S2 = lists:foldl(
                           fun(M, Acc) when M =/= From ->
                                   case is_muted(M, From, Mutes) of
                                       true  -> Acc;
                                       false -> send_or_queue(M, Frame, Acc)
                                   end;
                              (_, Acc) -> Acc
                           end, S, Members),
                    registry(S2);
                _ -> registry(S)
            end;

        {msg_amend, From, To, Type, F} when is_binary(To) ->
            case is_muted(To, From, Mutes) of
                true -> registry(S);
                false -> registry(send_or_queue(To, amend_frame(From, Type, F, undefined), S))
            end;

        {msg_amend_g, From, Gid, Type, F} ->
            case maps:get(Gid, Gs, undefined) of
                #{members := Members} ->
                    Frame = amend_frame(From, Type, F, Gid),
                    S2 = lists:foldl(
                           fun(M, Acc) when M =/= From ->
                                   case is_muted(M, From, Mutes) of
                                       true  -> Acc;
                                       false -> send_or_queue(M, Frame, Acc)
                                   end;
                              (_, Acc) -> Acc
                           end, S, Members),
                    registry(S2);
                _ ->
                    send_online(From, err(<<"no such group">>), Online),
                    registry(S)
            end;

        {media_direct, From, To, F} ->
            case maps:is_key(To, Users) of
                false ->
                    send_online(From, err(<<"unknown user">>), Online),
                    registry(S);
                true ->
                    Id = maps:get(<<"id">>, F),
                    send_online(From, receipt(Id, <<"sent">>, To), Online),
                    case is_muted(To, From, Mutes) of
                        true ->
                            registry(S);
                        false ->
                            Frame = (maps:remove(<<"to">>, F))#{
                                      <<"type">> => <<"media">>,
                                      <<"from">> => From,
                                      <<"ts">> => erlang:system_time(millisecond)},
                            io:format("Media: ~s -> ~s~n", [From, To]),
                            registry(send_or_queue(To, Frame, S))
                    end
            end;

        {media_group, From, Gid, F} ->
            case maps:get(Gid, Gs, undefined) of
                undefined ->
                    send_online(From, err(<<"no such group">>), Online),
                    registry(S);
                #{name := Name, members := Members} ->
                    case lists:member(From, Members) of
                        false ->
                            send_online(From, err(<<"not a group member">>), Online),
                            registry(S);
                        true ->
                            Id = maps:get(<<"id">>, F),
                            send_online(From, receipt(Id, <<"sent">>, Gid), Online),
                            Frame = F#{<<"type">> => <<"media">>,
                                       <<"from">> => From,
                                       <<"group">> => Gid,
                                       <<"group_name">> => Name,
                                       <<"ts">> => erlang:system_time(millisecond)},
                            io:format("GMedia: ~s -> ~s~n", [From, Gid]),
                            S2 = lists:foldl(
                                   fun(M, Acc) when M =/= From ->
                                           case is_muted(M, From, Mutes) of
                                               true  -> Acc;
                                               false -> send_or_queue(M, Frame, Acc)
                                           end;
                                      (_, Acc) -> Acc
                                   end, S, Members),
                            registry(S2)
                    end
            end;

        {create_group, Creator, Name, Members0} ->
            Members = lists:usort([Creator | [M || M <- Members0, is_binary(M)]]),
            Gid = list_to_binary("g" ++ maps:get(gpfx, S, "0")
                                     ++ "-" ++ integer_to_list(maps:get(gseq, S))),

            G = #{name => Name, members => Members,
                  owner => Creator, admins => [Creator]},
            Frame = (group_view(Gid, G))#{<<"type">> => <<"group_created">>,
                                          <<"by">> => Creator},
            S2 = lists:foldl(fun(M, Acc) -> send_or_queue(M, Frame, Acc) end,
                             S, Members),
            io:format("Group ~s (~s) by ~s~n", [Gid, Name, Creator]),
            registry(S2#{groups := Gs#{Gid => G},
                         users := learn(Members, Users),
                         gseq := maps:get(gseq, S) + 1});

        {restore_groups, Stored} ->
            {Gs2, Users2, N} =
                lists:foldl(
                  fun({Gid, G}, {AccG, AccU, AccN}) ->
                          case maps:is_key(Gid, AccG) of
                              true  -> {AccG, AccU, AccN};
                              false -> {AccG#{Gid => G},
                                        learn(maps:get(members, G), AccU),
                                        AccN + 1}
                          end
                  end, {Gs, Users, 0}, Stored),
            case N of
                0 -> ok;
                _ -> io:format("Group restore: ~p group(s) reloaded from the database~n", [N])
            end,
            registry(S#{groups := Gs2, users := Users2});

        {add_members, User, Gid, Wanted} ->
            with_admin(User, Gid, Gs, Online,
                fun(G) ->
                    Members = maps:get(members, G),
                    New = [M || M <- Wanted, is_binary(M), not lists:member(M, Members)],
                    case New of
                        [] ->
                            send_online(User, err(<<"nobody to add">>), Online),
                            S;
                        _ ->
                            G2 = G#{members := lists:usort(Members ++ New)},
                            announce_group(Gid, G2, User, <<"added">>, New,
                                           S#{groups := Gs#{Gid => G2},
                                              users := learn(New, Users)})
                    end
                end, S);

        {set_admin, User, Gid, Target, On} ->
            with_admin(User, Gid, Gs, Online,
                fun(G) ->
                    Members = maps:get(members, G),
                    Owner = maps:get(owner, G, undefined),
                    Admins = maps:get(admins, G, []),
                    case {lists:member(Target, Members), Target =:= Owner} of
                        {false, _} ->
                            send_online(User, err(<<"not a group member">>), Online),
                            S;
                        {_, true} ->
                            send_online(User, err(<<"the owner is always an admin">>), Online),
                            S;
                        _ ->
                            Admins2 = case On of
                                          true  -> lists:usort([Target | Admins]);
                                          false -> lists:delete(Target, Admins)
                                      end,
                            G2 = G#{admins => Admins2},
                            Action = case On of
                                         true  -> <<"promoted">>;
                                         false -> <<"demoted">>
                                     end,
                            announce_group(Gid, G2, User, Action, [Target],
                                           S#{groups := Gs#{Gid => G2}})
                    end
                end, S);

        {remove_member, User, Gid, Target} ->
            with_admin(User, Gid, Gs, Online,
                fun(G) ->
                    Members = maps:get(members, G),

                    case {lists:member(Target, Members),
                          Target =:= User,
                          Target =:= maps:get(owner, G, undefined)} of
                        {false, _, _} ->
                            send_online(User, err(<<"not a group member">>), Online),
                            S;
                        {_, true, _} ->
                            send_online(User, err(<<"leave the group instead">>), Online),
                            S;
                        {_, _, true} ->
                            send_online(User, err(<<"the owner cannot be removed">>), Online),
                            S;
                        _ ->
                            G2 = G#{members := lists:delete(Target, Members),
                                    admins => lists:delete(Target, maps:get(admins, G, []))},
                            announce_group(Gid, G2, User, <<"removed">>, [Target],
                                           [Target], S#{groups := Gs#{Gid => G2}})
                    end
                end, S);

        {leave_group, User, Gid} ->
            case maps:get(Gid, Gs, undefined) of
                undefined ->
                    send_online(User, err(<<"no such group">>), Online),
                    registry(S);
                G ->
                    case lists:member(User, maps:get(members, G)) of
                        false ->
                            send_online(User, err(<<"not a group member">>), Online),
                            registry(S);
                        true ->
                            registry(do_leave(User, Gid, G, S))
                    end
            end;

        {gmsg, From, Gid, Id, Text, Reply} ->
            case maps:get(Gid, Gs, undefined) of
                undefined ->
                    send_online(From, err(<<"no such group">>), Online),
                    registry(S);
                #{name := Name, members := Members} ->
                    case lists:member(From, Members) of
                        false ->
                            send_online(From, err(<<"not a group member">>), Online),
                            registry(S);
                        true ->
                            Ts = erlang:system_time(millisecond),
                            send_online(From, receipt(Id, <<"sent">>, Gid), Online),
                            Frame = maps:merge(Reply,
                                      #{<<"type">> => <<"gmsg">>, <<"group">> => Gid,
                                        <<"group_name">> => Name, <<"id">> => Id,
                                        <<"from">> => From, <<"text">> => Text,
                                        <<"ts">> => Ts}),
                            io:format("GMsg: ~s -> ~s~n", [From, Gid]),

                            S2 = lists:foldl(
                                   fun(M, Acc) when M =/= From ->
                                           case is_muted(M, From, Mutes) of
                                               true  -> Acc;
                                               false -> send_or_queue(M, Frame, Acc)
                                           end;
                                      (_, Acc) -> Acc
                                   end, S, Members),
                            registry(S2)
                    end
            end;

        {typing_d, From, To} ->
            case is_muted(To, From, Mutes) of
                true  -> ok;
                false -> send_online(To, #{<<"type">> => <<"typing">>,
                                           <<"from">> => From}, Online)
            end,
            registry(S);

        {typing_g, From, Gid} ->
            case maps:get(Gid, Gs, undefined) of
                #{members := Members} ->
                    Frame = #{<<"type">> => <<"typing">>, <<"from">> => From,
                              <<"group">> => Gid},
                    [send_online(M, Frame, Online) || M <- Members, M =/= From,
                                                      not is_muted(M, From, Mutes)];
                _ -> ok
            end,
            registry(S);

        {call, From, To, Type, Extra} ->
            Reachable = maps:find(To, Online),
            Blocked = is_muted(To, From, Mutes),
            case {Reachable, Blocked} of
                {{ok, Pid}, false} ->
                    Pid ! {push, maps:merge(Extra, #{<<"type">> => Type,
                                                     <<"from">> => From})},
                    io:format("Call ~s: ~s -> ~s~n", [Type, From, To]);
                _ ->

                    case is_ring_start(Type) of
                        true ->
                            send_online(From, #{<<"type">> => <<"call_failed">>,
                                                <<"user">> => To,
                                                <<"reason">> => <<"offline">>}, Online);
                        false -> ok
                    end
            end,
            registry(S)
    end.

is_ring_start(<<"call_invite">>) -> true;
is_ring_start(<<"call_offer">>)  -> true;
is_ring_start(_)                 -> false.

group_view(Gid, G) ->
    #{<<"id">> => Gid,
      <<"name">> => maps:get(name, G),
      <<"members">> => maps:get(members, G),
      <<"admins">> => maps:get(admins, G, []),
      <<"owner">> => maps:get(owner, G, null)}.

is_group_admin(User, G) ->
    lists:member(User, maps:get(admins, G, [])).

do_leave(User, Gid, G, S = #{groups := Gs}) ->
    Members2 = lists:delete(User, maps:get(members, G)),
    Admins2 = lists:delete(User, maps:get(admins, G, [])),
    case Members2 of
        [] ->
            io:format("Group ~s deleted (last member ~s left)~n", [Gid, User]),

            Frame = (group_view(Gid, G#{members := []}))
                        #{<<"type">> => <<"group_updated">>,
                          <<"by">> => User, <<"action">> => <<"left">>,
                          <<"targets">> => [User]},
            send_or_queue(User, Frame, S#{groups := maps:remove(Gid, Gs)});
        _ ->
            {Owner2, Admins3} =
                case maps:get(owner, G, undefined) of
                    User ->
                        Heir = case Admins2 of
                                   [A | _] -> A;
                                   [] -> hd(Members2)
                               end,
                        {Heir, lists:usort([Heir | Admins2])};
                    Owner ->
                        {Owner, Admins2}
                end,
            G2 = G#{members := Members2, admins => Admins3, owner => Owner2},
            announce_group(Gid, G2, User, <<"left">>, [User], [User],
                           S#{groups := Gs#{Gid => G2}})
    end.

learn(Names, Users) ->
    lists:foldl(fun(N, Acc) -> Acc#{N => maps:get(N, Acc, 0)} end, Users, Names).

with_admin(User, Gid, Gs, Online, Fun, S) ->
    case maps:get(Gid, Gs, undefined) of
        undefined ->
            send_online(User, err(<<"no such group">>), Online),
            registry(S);
        G ->
            case is_group_admin(User, G) of
                false ->
                    send_online(User, err(<<"Not in this group">>), Online),
                    registry(S);
                true ->
                    registry(Fun(G))
            end
    end.

announce_group(Gid, G, By, Action, Targets, S) ->
    announce_group(Gid, G, By, Action, Targets, [], S).

announce_group(Gid, G, By, Action, Targets, Extra, S) ->
    Frame = (group_view(Gid, G))#{<<"type">> => <<"group_updated">>,
                                  <<"by">> => By,
                                  <<"action">> => Action,
                                  <<"targets">> => Targets},
    io:format("Group ~s: ~s ~s by ~s~n",
              [Gid, Action, iolist_to_binary(lists:join(",", Targets)), By]),
    lists:foldl(fun(M, Acc) -> send_or_queue(M, Frame, Acc) end,
                S, lists:usort(maps:get(members, G) ++ Extra)).

receipt(Id, Status, Peer) ->
    #{<<"type">> => <<"receipt">>, <<"id">> => Id,
      <<"status">> => Status, <<"peer">> => Peer}.

err(Reason) -> #{<<"type">> => <<"error">>, <<"reason">> => Reason}.

broadcast_presence(Frame, Subject, Online, Mutes) ->
    maps:foreach(fun(U, _) when U =:= Subject -> ok;
                    (U, Pid) ->
                         case is_muted(U, Subject, Mutes) of
                             true  -> ok;
                             false -> Pid ! {push, Frame}
                         end
                 end, Online).

is_muted(Muter, Target, Mutes) ->
    maps:is_key(Target, maps:get(Muter, Mutes, #{})).

reply_of(F) ->
    lists:foldl(fun(K, Acc) ->
                    case maps:get(K, F, undefined) of
                        undefined -> Acc;
                        V -> Acc#{K => V}
                    end
                end, #{}, [<<"reply_to">>, <<"reply_text">>, <<"reply_from">>]).

reaction_frame(From, F, Gid) ->
    Base = #{<<"type">> => <<"reaction">>, <<"from">> => From,
             <<"target">> => maps:get(<<"target">>, F),
             <<"emoji">> => maps:get(<<"emoji">>, F),
             <<"op">> => maps:get(<<"op">>, F, <<"add">>)},
    case Gid of
        undefined -> Base;
        _ -> Base#{<<"group">> => Gid}
    end.

amend_frame(From, Type, F, Gid) ->
    Base = #{<<"type">> => Type, <<"from">> => From,
             <<"target">> => maps:get(<<"target">>, F),
             <<"ts">> => erlang:system_time(millisecond)},
    Base2 = case Type of
                <<"msg_edit">> -> Base#{<<"text">> => maps:get(<<"text">>, F)};
                _ -> Base
            end,
    case Gid of
        undefined -> Base2;
        _ -> Base2#{<<"group">> => Gid}
    end.

send_online(U, Frame, Online) ->
    case maps:find(U, Online) of
        {ok, Pid} -> Pid ! {push, Frame};
        error -> ok
    end.

send_or_queue(U, Frame, S = #{online := Online, queues := Qs}) ->
    case maps:find(U, Online) of
        {ok, Pid} ->
            Pid ! {push, Frame},
            S;
        error ->
            S#{queues := Qs#{U => [Frame | maps:get(U, Qs, [])]}}
    end.

reply(Socket, Map) ->
    gen_tcp:send(Socket, [json:encode(Map), $\n]).

decode(Line) ->
    try {ok, json:decode(strip(Line))}
    catch _:_ -> error
    end.

peer_ip(Socket) ->
    {ok, {Addr, _}} = inet:peername(Socket),
    list_to_binary(inet:ntoa(Addr)).

call_extra(Socket, F) ->
    Extra0 = #{<<"ip">> => peer_ip(Socket),
               <<"udp_port">> => maps:get(<<"udp_port">>, F)},
    case F of
        #{<<"vport">> := VP} ->
            Extra0#{<<"vport">> => VP,
                    <<"video">> => maps:get(<<"video">>, F, true)};
        _ -> Extra0
    end.

strip(Bin) ->
    S1 = case Bin of
             <<Body:(byte_size(Bin) - 1)/binary, $\n>> -> Body;
             _ -> Bin
         end,
    case S1 of
        <<Body2:(byte_size(S1) - 1)/binary, $\r>> -> Body2;
        _ -> S1
    end.
