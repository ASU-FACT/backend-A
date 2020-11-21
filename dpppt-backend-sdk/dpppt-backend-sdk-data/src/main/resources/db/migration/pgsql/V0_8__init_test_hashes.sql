CREATE TABLE "t_test_hashes"(
 "pk_test_hash_id" Serial NOT NULL,
 "hash" Text NOT NULL,
 "received_at" Timestamp with time zone DEFAULT now() NOT NULL
)
WITH (autovacuum_enabled=true);

-- Add keys for table t_test_hashes

ALTER TABLE "t_test_hashes" ADD CONSTRAINT "PK_t_test_hashes" PRIMARY KEY ("pk_test_hash_id");

ALTER TABLE "t_test_hashes" ADD CONSTRAINT "testhash" UNIQUE ("hash");