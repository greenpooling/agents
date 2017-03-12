--
-- PostgreSQL database dump
--

-- Dumped from database version 9.5.3
-- Dumped by pg_dump version 9.6.1

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET search_path = public, pg_catalog;

--
-- Name: notify_trigger(); Type: FUNCTION; Schema: public; Owner: debug
--

CREATE FUNCTION notify_trigger() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
BEGIN
PERFORM pg_notify('watchers', TG_TABLE_NAME || ',' || NEW.id );
RETURN new;
END;
$$;


ALTER FUNCTION public.notify_trigger() OWNER TO debug;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: carpools; Type: TABLE; Schema: public; Owner: debug
--

CREATE TABLE carpools (
    id integer NOT NULL,
    capacity integer NOT NULL,
    origin integer NOT NULL,
    destination integer NOT NULL,
    date date NOT NULL,
    tdepart time without time zone NOT NULL,
    tarrive time without time zone NOT NULL,
    organiser integer NOT NULL,
    state integer,
    dbgmemcount integer,
    roundtrip text
);


ALTER TABLE carpools OWNER TO debug;

--
-- Name: carpools_id_seq; Type: SEQUENCE; Schema: public; Owner: debug
--

CREATE SEQUENCE carpools_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE carpools_id_seq OWNER TO debug;

--
-- Name: carpools_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: debug
--

ALTER SEQUENCE carpools_id_seq OWNED BY carpools.id;


--
-- Name: proposals; Type: TABLE; Schema: public; Owner: debug
--

CREATE TABLE proposals (
    id integer NOT NULL,
    uid integer NOT NULL,
    cid integer NOT NULL,
    accepted integer,
    cost double precision,
    separation integer
);


ALTER TABLE proposals OWNER TO debug;

--
-- Name: proposals_id_seq; Type: SEQUENCE; Schema: public; Owner: debug
--

CREATE SEQUENCE proposals_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE proposals_id_seq OWNER TO debug;

--
-- Name: proposals_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: debug
--

ALTER SEQUENCE proposals_id_seq OWNED BY proposals.id;


--
-- Name: ucintermediary; Type: TABLE; Schema: public; Owner: debug
--

CREATE TABLE ucintermediary (
    id integer NOT NULL,
    uid integer NOT NULL,
    cid integer NOT NULL
);


ALTER TABLE ucintermediary OWNER TO debug;

--
-- Name: ucintermediary_id_seq; Type: SEQUENCE; Schema: public; Owner: debug
--

CREATE SEQUENCE ucintermediary_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE ucintermediary_id_seq OWNER TO debug;

--
-- Name: ucintermediary_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: debug
--

ALTER SEQUENCE ucintermediary_id_seq OWNED BY ucintermediary.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: debug
--

CREATE TABLE users (
    id integer NOT NULL,
    email character varying(50) NOT NULL,
    forename character varying(50) NOT NULL,
    surname character varying(50) NOT NULL,
    department character varying(50) NOT NULL
);


ALTER TABLE users OWNER TO debug;

--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: debug
--

CREATE SEQUENCE users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE users_id_seq OWNER TO debug;

--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: debug
--

ALTER SEQUENCE users_id_seq OWNED BY users.id;


--
-- Name: carpools id; Type: DEFAULT; Schema: public; Owner: debug
--

ALTER TABLE ONLY carpools ALTER COLUMN id SET DEFAULT nextval('carpools_id_seq'::regclass);


--
-- Name: proposals id; Type: DEFAULT; Schema: public; Owner: debug
--

ALTER TABLE ONLY proposals ALTER COLUMN id SET DEFAULT nextval('proposals_id_seq'::regclass);


--
-- Name: ucintermediary id; Type: DEFAULT; Schema: public; Owner: debug
--

ALTER TABLE ONLY ucintermediary ALTER COLUMN id SET DEFAULT nextval('ucintermediary_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: debug
--

ALTER TABLE ONLY users ALTER COLUMN id SET DEFAULT nextval('users_id_seq'::regclass);


--
-- Name: carpools carpools_pkey; Type: CONSTRAINT; Schema: public; Owner: debug
--

ALTER TABLE ONLY carpools
    ADD CONSTRAINT carpools_pkey PRIMARY KEY (id);


--
-- Name: ucintermediary ucintermediary_pkey; Type: CONSTRAINT; Schema: public; Owner: debug
--

ALTER TABLE ONLY ucintermediary
    ADD CONSTRAINT ucintermediary_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: debug
--

ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users watched_table_trigger; Type: TRIGGER; Schema: public; Owner: debug
--

CREATE TRIGGER watched_table_trigger AFTER INSERT ON users FOR EACH ROW EXECUTE PROCEDURE notify_trigger();


--
-- Name: carpools watched_table_trigger; Type: TRIGGER; Schema: public; Owner: debug
--

CREATE TRIGGER watched_table_trigger AFTER INSERT ON carpools FOR EACH ROW EXECUTE PROCEDURE notify_trigger();


--
-- Name: carpools carpools_organiser_fkey; Type: FK CONSTRAINT; Schema: public; Owner: debug
--

ALTER TABLE ONLY carpools
    ADD CONSTRAINT carpools_organiser_fkey FOREIGN KEY (organiser) REFERENCES users(id);


--
-- Name: proposals proposals_cid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: debug
--

ALTER TABLE ONLY proposals
    ADD CONSTRAINT proposals_cid_fkey FOREIGN KEY (cid) REFERENCES carpools(id);


--
-- Name: proposals proposals_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: debug
--

ALTER TABLE ONLY proposals
    ADD CONSTRAINT proposals_uid_fkey FOREIGN KEY (uid) REFERENCES users(id);


--
-- Name: ucintermediary ucintermediary_cid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: debug
--

ALTER TABLE ONLY ucintermediary
    ADD CONSTRAINT ucintermediary_cid_fkey FOREIGN KEY (cid) REFERENCES carpools(id);


--
-- Name: ucintermediary ucintermediary_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: debug
--

ALTER TABLE ONLY ucintermediary
    ADD CONSTRAINT ucintermediary_uid_fkey FOREIGN KEY (uid) REFERENCES users(id);


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- Name: carpools; Type: ACL; Schema: public; Owner: debug
--

REVOKE ALL ON TABLE carpools FROM PUBLIC;
REVOKE ALL ON TABLE carpools FROM debug;
GRANT ALL ON TABLE carpools TO debug;
GRANT ALL ON TABLE carpools TO jade;


--
-- Name: carpools_id_seq; Type: ACL; Schema: public; Owner: debug
--

REVOKE ALL ON SEQUENCE carpools_id_seq FROM PUBLIC;
REVOKE ALL ON SEQUENCE carpools_id_seq FROM debug;
GRANT ALL ON SEQUENCE carpools_id_seq TO debug;
GRANT SELECT,USAGE ON SEQUENCE carpools_id_seq TO jade;


--
-- Name: proposals; Type: ACL; Schema: public; Owner: debug
--

REVOKE ALL ON TABLE proposals FROM PUBLIC;
REVOKE ALL ON TABLE proposals FROM debug;
GRANT ALL ON TABLE proposals TO debug;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE proposals TO jade;


--
-- Name: proposals_id_seq; Type: ACL; Schema: public; Owner: debug
--

REVOKE ALL ON SEQUENCE proposals_id_seq FROM PUBLIC;
REVOKE ALL ON SEQUENCE proposals_id_seq FROM debug;
GRANT ALL ON SEQUENCE proposals_id_seq TO debug;
GRANT SELECT,USAGE ON SEQUENCE proposals_id_seq TO jade;


--
-- Name: ucintermediary; Type: ACL; Schema: public; Owner: debug
--

REVOKE ALL ON TABLE ucintermediary FROM PUBLIC;
REVOKE ALL ON TABLE ucintermediary FROM debug;
GRANT ALL ON TABLE ucintermediary TO debug;
GRANT ALL ON TABLE ucintermediary TO jade;


--
-- Name: ucintermediary_id_seq; Type: ACL; Schema: public; Owner: debug
--

REVOKE ALL ON SEQUENCE ucintermediary_id_seq FROM PUBLIC;
REVOKE ALL ON SEQUENCE ucintermediary_id_seq FROM debug;
GRANT ALL ON SEQUENCE ucintermediary_id_seq TO debug;
GRANT SELECT,USAGE ON SEQUENCE ucintermediary_id_seq TO jade;


--
-- Name: users; Type: ACL; Schema: public; Owner: debug
--

REVOKE ALL ON TABLE users FROM PUBLIC;
REVOKE ALL ON TABLE users FROM debug;
GRANT ALL ON TABLE users TO debug;
GRANT SELECT,INSERT ON TABLE users TO jade;


--
-- Name: users_id_seq; Type: ACL; Schema: public; Owner: debug
--

REVOKE ALL ON SEQUENCE users_id_seq FROM PUBLIC;
REVOKE ALL ON SEQUENCE users_id_seq FROM debug;
GRANT ALL ON SEQUENCE users_id_seq TO debug;
GRANT SELECT,USAGE ON SEQUENCE users_id_seq TO jade;


--
-- Name: DEFAULT PRIVILEGES FOR TABLES; Type: DEFAULT ACL; Schema: public; Owner: jade
--

ALTER DEFAULT PRIVILEGES FOR ROLE jade IN SCHEMA public REVOKE ALL ON TABLES  FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE jade IN SCHEMA public REVOKE ALL ON TABLES  FROM jade;
ALTER DEFAULT PRIVILEGES FOR ROLE jade IN SCHEMA public GRANT SELECT,INSERT,DELETE,UPDATE ON TABLES  TO jade;


--
-- PostgreSQL database dump complete
--

