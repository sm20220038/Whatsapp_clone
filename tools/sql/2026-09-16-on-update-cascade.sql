USE waclone;
ALTER TABLE participant DROP FOREIGN KEY FK5mkbmcdbhnooj3v3ubcnreah9, DROP FOREIGN KEY FKsiftd56p4vnlfthffmf07xhng;
ALTER TABLE participant ADD CONSTRAINT fk_participant_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON UPDATE CASCADE,
                        ADD CONSTRAINT fk_participant_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (conversation_id) ON UPDATE CASCADE;
ALTER TABLE message DROP FOREIGN KEY FK6yskk3hxw5sklwgi25y6d5u1l, DROP FOREIGN KEY FKbi5avhe69aol2mb1lnm6r4o2p;
ALTER TABLE message ADD CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (conversation_id) ON UPDATE CASCADE,
                    ADD CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES users (user_id) ON UPDATE CASCADE;
ALTER TABLE call_log DROP FOREIGN KEY FK8yftey8urtujs14d4b13rcjnc, DROP FOREIGN KEY FKccnb137byc2r8eeils9ndlp5b;
ALTER TABLE call_log ADD CONSTRAINT fk_call_log_caller FOREIGN KEY (caller_id) REFERENCES users (user_id) ON UPDATE CASCADE,
                     ADD CONSTRAINT fk_call_log_callee FOREIGN KEY (callee_id) REFERENCES users (user_id) ON UPDATE CASCADE;
ALTER TABLE key_bundle DROP FOREIGN KEY FKlttkbt8488a9voyfd0mm3crlq;
ALTER TABLE key_bundle ADD CONSTRAINT fk_key_bundle_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON UPDATE CASCADE;
ALTER TABLE mute DROP FOREIGN KEY FK2r1xgmj6sidfuvi9ec418etic, DROP FOREIGN KEY FKmb4luemxvaasiokw6llii8fmk;
ALTER TABLE mute ADD CONSTRAINT fk_mute_muter FOREIGN KEY (muter_id) REFERENCES users (user_id) ON UPDATE CASCADE,
                 ADD CONSTRAINT fk_mute_muted FOREIGN KEY (muted_id) REFERENCES users (user_id) ON UPDATE CASCADE;
ALTER TABLE reaction DROP FOREIGN KEY FK52m11aguxqx0uje12knj056jw;
ALTER TABLE reaction ADD CONSTRAINT fk_reaction_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON UPDATE CASCADE;
