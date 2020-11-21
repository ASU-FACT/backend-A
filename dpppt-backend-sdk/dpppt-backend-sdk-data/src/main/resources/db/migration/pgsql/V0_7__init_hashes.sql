CREATE TABLE "t_exposed_hashes"(
 "pk_exposed_hash_id" Serial NOT NULL,
 "hash" Text NOT NULL,
 "received_at" Timestamp with time zone DEFAULT now() NOT NULL,
 "app_source" Character varying(50) NOT NULL
)
WITH (autovacuum_enabled=true);

-- Add keys for table t_exposed_hashes

ALTER TABLE "t_exposed_hashes" ADD CONSTRAINT "PK_t_exposed_hashes" PRIMARY KEY ("pk_exposed_hash_id");

ALTER TABLE "t_exposed_hashes" ADD CONSTRAINT "hash" UNIQUE ("hash");