-- public.links definition

-- Drop table

-- DROP TABLE public.links;

CREATE TABLE public.links (
	id varchar NOT NULL,
	workflow varchar NOT NULL,
	stage int2 NOT NULL,
	salt varchar NULL,
	passwordless bool NULL,
	enc_metadata varchar NULL,
	seed_hash varchar NULL,
	"date" timestamp NOT NULL,
	finished bool NOT NULL,
	num_files int2 NULL,
	sender_pk varchar NULL,
	file_ids _varchar NULL,
	wrapped_requester_sk varchar NULL,
	requester_pk varchar NULL,
	life_expectancy int4 NULL DEFAULT 7,
	CONSTRAINT links_pkey PRIMARY KEY (id)
);