/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.data;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.dpppt.backend.sdk.model.Exposee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class JDBCDPPPTDataServiceImpl implements DPPPTDataService {

	private static final Logger logger = LoggerFactory.getLogger(JDBCDPPPTDataServiceImpl.class);
	private static final String PGSQL = "pgsql";
	private final String dbType;
	private final NamedParameterJdbcTemplate jt;

	public JDBCDPPPTDataServiceImpl(String dbType, DataSource dataSource) {
		this.dbType = dbType;
		this.jt = new NamedParameterJdbcTemplate(dataSource);
	}

	@Override
	@Transactional(readOnly = false)
	public void upsertExposee(Exposee exposee, String appSource) {
		String sql = null;
		if (dbType.equals(PGSQL)) {
			sql = "insert into t_exposed (key, key_date, app_source) values (:key, :key_date, :app_source)"
					+ " on conflict on constraint key do nothing";
		} else {
			sql = "merge into t_exposed using (values(cast(:key as varchar(10000)), cast(:key_date as date), cast(:app_source as varchar(50))))"
					+ " as vals(key, key_date, app_source) on t_exposed.key = vals.key"
					+ " when not matched then insert (key, key_date, app_source) values (vals.key, vals.key_date, vals.app_source)";
		}
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("key", exposee.getKey());
		params.addValue("app_source", appSource);
		params.addValue("key_date", new Date(exposee.getKeyDate()));
		jt.update(sql, params);
	}
	@Override
	@Transactional(readOnly = false)
	public void upsertTestHashes(ArrayList<String> testHashes) {
		System.out.println(testHashes.size()+"|"+testHashes.get(0));
		for(String hash:testHashes){
			String sql = null;
			if (dbType.equals(PGSQL)) {
				sql = "insert into t_test_hashes (hash) values (:hash)"
						+ " on conflict on constraint testhash do nothing";
			} else {
				sql = "merge into t_test_hashes using (values(cast(:hash as varchar(10000))))"
						+ " as vals(hash) on t_test_hashes.hash = vals.hash"
						+ " when not matched then insert (hash) values (vals.hash)";
			}
			MapSqlParameterSource params = new MapSqlParameterSource();
			params.addValue("hash", hash);
			jt.update(sql, params);
		}
		System.out.println("Done inserting test hashes into database");
	}
	@Override
	@Transactional(readOnly = false)
	public void upsertExposeeHashes(Exposee exposee, String appSource) {
		for(String hash:exposee.getHashes()){
			String sql = null;
			if (dbType.equals(PGSQL)) {
				sql = "insert into t_exposed_hashes (hash, app_source) values (:hash, :app_source)"
						+ " on conflict on constraint hash do nothing";
			} else {
				sql = "merge into t_exposed_hashes using (values(cast(:hash as varchar(10000)), cast(:app_source as varchar(50))))"
						+ " as vals(hash, app_source) on t_exposed_hashes.hash = vals.hash"
						+ " when not matched then insert (hash, app_source) values (vals.hash, vals.app_source)";
			}
			MapSqlParameterSource params = new MapSqlParameterSource();
			params.addValue("hash", hash);
			params.addValue("app_source", appSource);
			jt.update(sql, params);
		}
	}


	@Override
	@Transactional(readOnly = false)
	public void upsertExposees(List<Exposee> exposees, String appSource) {
		String sql = null;
		if (dbType.equals(PGSQL)) {
			sql = "insert into t_exposed (key, key_date, app_source) values (:key, :key_date, :app_source)"
					+ " on conflict on constraint key do nothing";
		} else {
			sql = "merge into t_exposed using (values(cast(:key as varchar(10000)), cast(:key_date as date), cast(:app_source as varchar(50))))"
					+ " as vals(key, key_date, app_source) on t_exposed.key = vals.key"
					+ " when not matched then insert (key, key_date, app_source) values (vals.key, vals.key_date, vals.app_source)";
		}
		var parameterList = new ArrayList<MapSqlParameterSource>();
		for(var exposee : exposees) {
			MapSqlParameterSource params = new MapSqlParameterSource();
			params.addValue("key", exposee.getKey());
			params.addValue("app_source", appSource);
			params.addValue("key_date", new Date(exposee.getKeyDate()));
			parameterList.add(params);
		}
		jt.batchUpdate(sql, parameterList.toArray(new MapSqlParameterSource[0]));
	}

	@Override
	@Transactional(readOnly = true)
	public int getMaxExposedIdForBatchReleaseTime(long batchReleaseTime, long batchLength) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("batchReleaseTime", Date.from(Instant.ofEpochMilli(batchReleaseTime)));
		params.addValue("startBatch", Date.from(Instant.ofEpochMilli(batchReleaseTime - batchLength)));
		String sql = "select max(pk_exposed_id) from t_exposed where received_at >= :startBatch and received_at < :batchReleaseTime";
		Integer maxId = jt.queryForObject(sql, params, Integer.class);
		if (maxId == null) {
			return 0;
		} else {
			return maxId;
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<Exposee> getSortedExposedForBatchReleaseTime(long batchReleaseTime, long batchLength) {
		String sql = "select pk_exposed_id, key, key_date from t_exposed where received_at >= :startBatch and received_at < :batchReleaseTime order by pk_exposed_id desc";
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("batchReleaseTime", Date.from(Instant.ofEpochMilli(batchReleaseTime)));
		params.addValue("startBatch", Date.from(Instant.ofEpochMilli(batchReleaseTime - batchLength)));
		return jt.query(sql, params, new ExposeeRowMapper());
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> getSortedExposedHashesForTest(int count){
		String sql = "select pk_test_hash_id, hash from t_test_hashes order by pk_test_hash_id desc LIMIT "+ count;
		return jt.query(sql, new ExposeeHashesRowMapper());
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> getSortedExposedHashes() {
		String sql = "select pk_exposed_hash_id, hash from t_exposed_hashes order by pk_exposed_hash_id desc";
		return jt.query(sql, new ExposeeHashesRowMapper());
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> getSortedExposedHashesForBatchReleaseTime(long batchReleaseTime, long batchLength) {
		String sql = "select pk_exposed_hash_id, hash from t_exposed_hashes where received_at >= :startBatch and received_at < :batchReleaseTime order by pk_exposed_hash_id desc";
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("batchReleaseTime", Date.from(Instant.ofEpochMilli(batchReleaseTime)));
		params.addValue("startBatch", Date.from(Instant.ofEpochMilli(batchReleaseTime - batchLength)));
		return jt.query(sql, params, new ExposeeHashesRowMapper());
	}

	@Override
	@Transactional(readOnly = false)
	public void cleanDB(Duration retentionPeriod) {
		OffsetDateTime retentionTime = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).minus(retentionPeriod);
		logger.info("Cleanup DB entries before: " + retentionTime);
		MapSqlParameterSource params = new MapSqlParameterSource("retention_time", Date.from(retentionTime.toInstant()));
//		TODO Clean DB remove hashes
		String sqlExposed = "delete from t_exposed where received_at < :retention_time";
		String sqlExposedHashes = "delete from t_exposed_hashes where received_at < :retention_time";
		jt.update(sqlExposed, params);
		jt.update(sqlExposedHashes, params);

	}
}
