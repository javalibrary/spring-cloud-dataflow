/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.server.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.EmbeddedDataSourceConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.server.config.features.FeaturesProperties;
import org.springframework.cloud.dataflow.server.repository.support.DataflowRdbmsInitializer;
import org.springframework.cloud.dataflow.server.repository.support.SearchPageable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Glenn Renfro
 * @author Ilayaperumal Gopinathan
 * @author Gunnar Hillert
 * @author Janne Valkealahti
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { EmbeddedDataSourceConfiguration.class, PropertyPlaceholderAutoConfiguration.class,
		RdbmsStreamDefinitionRepositoryTests.TestConfig.class })
public class RdbmsStreamDefinitionRepositoryTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private StreamDefinitionRepository repository;

	private JdbcTemplate template;

	@Before
	public void setup() throws Exception {
		template = new JdbcTemplate(dataSource);
		template.execute("DELETE FROM STREAM_DEFINITIONS");
	}

	@Test
	public void testFindOne() {
		StreamDefinition definition1 = new StreamDefinition("stream1", "time | log");
		StreamDefinition definition2 = new StreamDefinition("stream2", "http | jdbc");
		StreamDefinition definition3 = new StreamDefinition("stream3", "twitterstream | hdfs");
		repository.save(definition1);
		repository.save(definition2);
		repository.save(definition3);

		assertThat(repository.findById("stream1")).hasValue(definition1);
		assertThat(repository.findById("stream2")).hasValue(definition2);
		assertThat(repository.findById("stream3")).hasValue(definition3);
	}

	@Test
	public void testFindAllNone() {
		Pageable pageable = new PageRequest(1, 10);

		Page<StreamDefinition> page = repository.findAll(pageable);

		assertEquals(page.getTotalElements(), 0);
		assertEquals(page.getNumber(), 1);
		assertEquals(page.getNumberOfElements(), 0);
		assertEquals(page.getSize(), 10);
		assertEquals(page.getContent().size(), 0);
	}

	@Test
	public void testFindAllPageable() {
		initializeRepository();
		Pageable pageable = new PageRequest(0, 10);

		Page<StreamDefinition> page = repository.findAll(pageable);

		assertEquals(page.getTotalElements(), 3);
		assertEquals(page.getNumber(), 0);
		assertEquals(page.getNumberOfElements(), 3);
		assertEquals(page.getSize(), 10);
		assertEquals(page.getContent().size(), 3);
	}

	@Test(expected = DuplicateStreamDefinitionException.class)
	public void testSaveDuplicate() {
		repository.save(new StreamDefinition("stream1", "time | log"));
		repository.save(new StreamDefinition("stream1", "time | log"));
	}

	@Test(expected = DuplicateStreamDefinitionException.class)
	public void testSaveAllDuplicate() {
		List<StreamDefinition> definitions = new ArrayList<>();
		definitions.add(new StreamDefinition("stream1", "time | log"));

		repository.save(new StreamDefinition("stream1", "time | log"));
		repository.saveAll(definitions);
	}

	@Test
	public void testFindOneNoneFound() {
		assertThat(repository.findById("notfound")).isEmpty();

		initializeRepository();

		assertThat(repository.findById("notfound")).isEmpty();
	}

	@Test
	public void testExists() {
		assertThat(repository.existsById("exists")).isFalse();

		repository.save(new StreamDefinition("exists", "time | log"));

		assertThat(repository.existsById("exists")).isTrue();
		assertThat(repository.existsById("nothere")).isFalse();
	}

	@Test
	public void testFindAll() {
		assertFalse(repository.findAll().iterator().hasNext());

		initializeRepository();

		Iterable<StreamDefinition> items = repository.findAll();

		int count = 0;
		for (@SuppressWarnings("unused")
				StreamDefinition item : items) {
			count++;
		}

		assertEquals(3, count);
	}

	@Test
	public void testFindAllSpecific() {
		assertFalse(repository.findAll().iterator().hasNext());

		initializeRepository();

		List<String> names = new ArrayList<>();
		names.add("stream1");
		names.add("stream2");

		Iterable<StreamDefinition> items = repository.findAllById(names);

		int count = 0;
		for (@SuppressWarnings("unused")
				StreamDefinition item : items) {
			count++;
		}

		assertEquals(2, count);
	}

	@Test
	public void testCount() {
		assertEquals(0, repository.count());

		initializeRepository();

		assertEquals(3, repository.count());
	}

	@Test
	public void testDeleteNotFound() {
		repository.deleteById("notFound");
	}

	@Test
	public void testDelete() {
		initializeRepository();

		assertThat(repository.findById("stream2")).isNotEmpty();

		repository.deleteById("stream2");

		assertThat(repository.findById("stream2")).isEmpty();
	}

	@Test
	public void testDeleteDefinition() {
		initializeRepository();

		assertThat(repository.findById("stream2")).isNotEmpty();

		repository.delete(new StreamDefinition("stream2", "time | log"));

		assertThat(repository.findById("stream2")).isEmpty();
	}

	@Test
	public void testDeleteMultipleDefinitions() {
		initializeRepository();

		assertThat(repository.findById("stream1")).isNotEmpty();
		assertThat(repository.findById("stream2")).isNotEmpty();

		repository.deleteAll(Arrays.asList(new StreamDefinition("stream1", "time | log"),
				new StreamDefinition("stream2", "time | log")));

		assertThat(repository.findById("stream1")).isEmpty();
		assertThat(repository.findById("stream2")).isEmpty();
	}

	@Test
	public void testDeleteAllNone() {
		repository.deleteAll();
	}

	@Test
	public void testDeleteAll() {
		initializeRepository();

		assertEquals(3, repository.count());
		repository.deleteAll();
		assertEquals(0, repository.count());
	}

	private void initializeRepository() {
		repository.save(new StreamDefinition("stream1", "time | log"));
		repository.save(new StreamDefinition("stream2", "time | log"));
		repository.save(new StreamDefinition("stream3", "time | log"));
	}

	private void initializeRepository(int count) {
		for (int i = 0; i < count; i++) {
			repository.save(new StreamDefinition("stream" + i, "time | log"));
		}
	}

	protected void initializeRepositoryNotInOrder() {
		repository.save(new StreamDefinition("stream2", "time | logB"));
		repository.save(new StreamDefinition("stream3", "time | logA"));
		repository.save(new StreamDefinition("stream1", "time | logC"));
	}

	@Test
	public void findAllSortTestASC() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.ASC, "DEFINITION"));
		String[] names = new String[] { "stream1", "stream2", "stream3" };
		findAllSort(sort, names);
	}

	@Test
	public void findAllPageableTestASC() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.ASC, "DEFINITION"));
		Pageable pageable = new PageRequest(0, 10, sort);
		String[] names = new String[] { "stream1", "stream2", "stream3" };
		findAllPageable(pageable, names);
	}

	@Test
	public void findAllSortTestDESC() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.DESC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.DESC, "DEFINITION"));
		String[] names = new String[] { "stream3", "stream2", "stream1" };
		findAllSort(sort, names);
	}

	@Test
	public void findAllPageableTestDESC() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.DESC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.DESC, "DEFINITION"));
		Pageable pageable = new PageRequest(0, 10, sort);

		String[] names = new String[] { "stream3", "stream2", "stream1" };
		findAllPageable(pageable, names);
	}

	@Test
	public void findAllSortTestASCNameOnly() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"));
		String[] names = new String[] { "stream1", "stream2", "stream3" };
		findAllSort(sort, names);
	}

	@Test
	public void findAllPageableTestASCNameOnly() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"));
		Pageable pageable = new PageRequest(0, 10, sort);

		String[] names = new String[] { "stream1", "stream2", "stream3" };
		findAllPageable(pageable, names);
	}

	@Test
	public void findAllSortTestASCDefinitionOnly() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.DESC, "DEFINITION"));
		String[] names = new String[] { "stream1", "stream2", "stream3" };
		findAllSort(sort, names);
	}

	@Test
	public void findAllPageableTestDESCDefinitionOnly() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.DESC, "DEFINITION"));
		Pageable pageable = new PageRequest(0, 10, sort);

		String[] names = new String[] { "stream1", "stream2", "stream3" };
		findAllPageable(pageable, names);
	}

	@Test
	public void findAllPageablePage2TestASC() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.ASC, "DEFINITION"));
		Pageable pageable = new PageRequest(1, 2, sort);
		String[] names = new String[] { "stream3" };
		findAllPageable(pageable, names);
	}

	@Test
	public void findAllPageablePage2TestDESC() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.DESC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.DESC, "DEFINITION"));
		Pageable pageable = new PageRequest(1, 2, sort);
		String[] names = new String[] { "stream1" };
		findAllPageable(pageable, names);
	}

	@Test
	public void findAllPageableDefinitionStringTestDESC() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.DESC, "DEFINITION"));
		Pageable pageable = new PageRequest(1, 2, sort);
		String[] names = new String[] { "stream3" };
		findAllPageable(pageable, names);
	}

	@Test
	public void findAllUsingSearchPageablePage2TestDESC() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.DESC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.DESC, "DEFINITION"));
		Pageable pageable = new PageRequest(1, 2, sort);
		final SearchPageable searchPageable = new SearchPageable(pageable, "stream");
		searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");

		String[] names = new String[] { "stream1" };
		findAllUsingSearchPageable(searchPageable, names);
	}

	// Search Tests

	@Test
	public void findAllUsingSearchPageableDefinitionStringTestDESC() {
		final Sort sort = new Sort(new Sort.Order(Sort.Direction.DESC, "DEFINITION"));
		final Pageable pageable = new PageRequest(0, 2, sort);
		final SearchPageable searchPageable = new SearchPageable(pageable, "stream");
		searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");

		String[] names = new String[] { "stream1", "stream2" };
		findAllUsingSearchPageable(searchPageable, names);
	}

	@Test
	public void findAllUsingSearchPageablePartialDefinitionStringTestDESC() {
		final Sort sort = new Sort(new Sort.Order(Sort.Direction.DESC, "DEFINITION"));
		final Pageable pageable = new PageRequest(0, 10, sort);
		final SearchPageable searchPageable = new SearchPageable(pageable, "eam");
		searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");

		String[] names = new String[] { "stream1", "stream2", "stream3" };
		findAllUsingSearchPageable(searchPageable, names);
	}

	@Test
	public void findAllUsingSearchPageableDefinitionStringTestDESC2() {
		final Sort sort = new Sort(new Sort.Order(Sort.Direction.DESC, "DEFINITION"));
		final Pageable pageable = new PageRequest(1, 2, sort);
		final SearchPageable searchPageable = new SearchPageable(pageable, "stream");
		searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");

		String[] names = new String[] { "stream3" };
		findAllUsingSearchPageable(searchPageable, names);
	}

	@Test
	public void findAllUsingSearchPageableTestASC1() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.ASC, "DEFINITION"));
		Pageable pageable = new PageRequest(0, 10, sort);

		final SearchPageable searchPageable = new SearchPageable(pageable, "stream");
		searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");

		String[] names = new String[] { "stream1", "stream2", "stream3" };
		findAllUsingSearchPageable(searchPageable, names);
	}

	@Test
	public void findAllUsingSearchPageableTestASC2() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.ASC, "DEFINITION"));
		Pageable pageable = new PageRequest(0, 10, sort);

		final SearchPageable searchPageable = new SearchPageable(pageable, "stream1");
		searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");

		String[] names = new String[] { "stream1" };
		findAllUsingSearchPageable(searchPageable, names);
	}

	@Test
	public void findAllUsingSearchPageableTestASC3() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.ASC, "DEFINITION"));
		Pageable pageable = new PageRequest(0, 10, sort);

		final SearchPageable searchPageable = new SearchPageable(pageable, "str");
		searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");

		String[] names = new String[] { "stream1", "stream2", "stream3" };
		findAllUsingSearchPageable(searchPageable, names);
	}

	@Test
	public void findAllUsingSearchPageableTestASC4() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.ASC, "DEFINITION"));
		Pageable pageable = new PageRequest(0, 10, sort);

		final SearchPageable searchPageable = new SearchPageable(pageable, "m1");
		searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");

		String[] names = new String[] { "stream1" };
		findAllUsingSearchPageable(searchPageable, names);
	}

	@Test
	public void findAllUsingSearchPageableTestASC5() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.ASC, "DEFINITION"));
		Pageable pageable = new PageRequest(0, 10, sort);

		final SearchPageable searchPageable = new SearchPageable(pageable, "m3");
		searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");

		String[] names = new String[] { "stream3" };
		findAllUsingSearchPageable(searchPageable, names);
	}

	@Test
	public void findAllUsingSearchPageableTestASC6() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.ASC, "DEFINITION"));
		Pageable pageable = new PageRequest(0, 10, sort);

		final SearchPageable searchPageable = new SearchPageable(pageable, "does not exist");
		searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");
		;

		String[] names = new String[] {};
		findAllUsingSearchPageable(searchPageable, names);
	}

	@Test
	public void findAllUsingSearchPageableTestASC7() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.ASC, "DEFINITION"));
		Pageable pageable = new PageRequest(0, 10, sort);

		final SearchPageable searchPageable = new SearchPageable(pageable, "logB");
		searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");
		;

		String[] names = new String[] { "stream2" };
		findAllUsingSearchPageable(searchPageable, names);
	}

	@Test
	public void findAllUsingSearchPageableTestASC8() {
		Sort sort = new Sort(new Sort.Order(Sort.Direction.ASC, "DEFINITION_NAME"),
				new Sort.Order(Sort.Direction.ASC, "DEFINITION"));
		Pageable pageable = new PageRequest(0, 10, sort);

		final SearchPageable searchPageable = new SearchPageable(pageable, "LOGB");
		searchPageable.addColumns("DEFINITION_NAME", "DEFINITION");

		String[] names = new String[] { "stream2" };
		findAllUsingSearchPageable(searchPageable, names);
	}

	@Test
	public void testSearchWithPagination() {
		initializeRepository(25);
		Pageable pageable1 = new PageRequest(0, 10);
		Pageable pageable2 = new PageRequest(1, 10);
		Pageable pageable3 = new PageRequest(2, 10);

		final SearchPageable searchPageable1 = new SearchPageable(pageable1, "stream10");
		searchPageable1.addColumns("DEFINITION_NAME", "DEFINITION");
		Page<StreamDefinition> page = repository.findByNameLike(searchPageable1);

		assertEquals(page.getTotalElements(), 1);
		assertEquals(page.getNumber(), 0);
		assertEquals(page.getNumberOfElements(), 1);
		assertEquals(page.getSize(), 10);
		assertEquals(page.getContent().size(), 1);

		final SearchPageable searchPageable2 = new SearchPageable(pageable1, "stream1");
		searchPageable2.addColumns("DEFINITION_NAME", "DEFINITION");
		page = repository.findByNameLike(searchPageable2);

		assertEquals(page.getTotalElements(), 11);
		assertEquals(page.getNumber(), 0);
		assertEquals(page.getNumberOfElements(), 10);
		assertEquals(page.getSize(), 10);
		assertEquals(page.getContent().size(), 10);

		final SearchPageable searchPageable3 = new SearchPageable(pageable2, "stream1");
		searchPageable3.addColumns("DEFINITION_NAME", "DEFINITION");
		page = repository.findByNameLike(searchPageable3);

		assertEquals(page.getTotalElements(), 11);
		assertEquals(page.getNumber(), 1);
		assertEquals(page.getNumberOfElements(), 1);
		assertEquals(page.getSize(), 10);
		assertEquals(page.getContent().size(), 1);

		final SearchPageable searchPageable4 = new SearchPageable(pageable3, "stream");
		searchPageable4.addColumns("DEFINITION_NAME", "DEFINITION");
		page = repository.findByNameLike(searchPageable4);

		assertEquals(page.getTotalElements(), 25);
		assertEquals(page.getNumber(), 2);
		assertEquals(page.getNumberOfElements(), 5);
		assertEquals(page.getSize(), 10);
		assertEquals(page.getContent().size(), 5);
	}

	private void findAllUsingSearchPageable(SearchPageable searchPageable, String[] expectedOrder) {

		assertFalse(repository.findAll().iterator().hasNext());
		initializeRepositoryNotInOrder();

		final Iterable<StreamDefinition> items = repository.findByNameLike(searchPageable);

		makeSortAssertions(items, expectedOrder);

	}

	private void findAllPageable(Pageable pageable, String[] expectedOrder) {

		assertFalse(repository.findAll().iterator().hasNext());
		initializeRepositoryNotInOrder();

		final Iterable<StreamDefinition> items = repository.findAll(pageable);

		makeSortAssertions(items, expectedOrder);

	}

	private void findAllSort(Sort sort, String[] expectedOrder) {
		assertFalse(repository.findAll().iterator().hasNext());
		initializeRepositoryNotInOrder();

		final Iterable<StreamDefinition> items = repository.findAll(sort);

		makeSortAssertions(items, expectedOrder);
	}

	private void makeSortAssertions(Iterable<StreamDefinition> items, String[] expectedOrder) {
		int count = 0;
		List<StreamDefinition> definitions = new ArrayList<>();
		for (StreamDefinition item : items) {
			definitions.add(item);
			count++;
		}

		assertEquals(expectedOrder.length, count);
		int currentDefinitionOffset = 0;
		for (String name : expectedOrder) {
			assertEquals("definition name retrieve was not in the order expected", name,
					definitions.get(currentDefinitionOffset++).getName());
		}
	}

	@Configuration
	static class TestConfig {

		@Bean
		public FeaturesProperties featuresProperties() {
			return new FeaturesProperties();
		}

		@Bean
		public DataflowRdbmsInitializer definitionRepositoryInitializer(DataSource dataSource) {
			DataflowRdbmsInitializer definitionRepositoryInitializer = new DataflowRdbmsInitializer(
					featuresProperties());
			definitionRepositoryInitializer.setDataSource(dataSource);
			return definitionRepositoryInitializer;
		}

		@Bean
		@ConditionalOnProperty(prefix = FeaturesProperties.FEATURES_PREFIX, name = FeaturesProperties.STREAMS_ENABLED, matchIfMissing = true)
		public StreamDefinitionRepository rdbmsStreamDefinitionRepository(DataSource dataSource) {
			return new RdbmsStreamDefinitionRepository(dataSource);
		}
	}

}
