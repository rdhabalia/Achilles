package info.archinnov.achilles.context;

import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import info.archinnov.achilles.context.AchillesFlushContext.FlushType;
import info.archinnov.achilles.type.ConsistencyLevel;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Query;
import com.datastax.driver.core.ResultSet;

/**
 * CQLImmediateFlushContextTest
 * 
 * @author DuyHai DOAN
 * 
 */

@RunWith(MockitoJUnitRunner.class)
public class CQLImmediateFlushContextTest
{

	private CQLImmediateFlushContext context;

	@Mock
	private CQLDaoContext daoContext;

	@Mock
	private BoundStatement bs;

	@Mock
	private Query query;

	@Before
	public void setUp()
	{
		context = new CQLImmediateFlushContext(daoContext);
	}

	@Test
	public void should_return_IMMEDIATE_type() throws Exception
	{
		assertThat(context.type()).isSameAs(FlushType.IMMEDIATE);
	}

	@Test
	public void should_push_statement_with_consistency() throws Exception
	{
		List<BoundStatement> boundStatements = new ArrayList<BoundStatement>();
		Whitebox.setInternalState(context, "boundStatements", boundStatements);

		context.pushBoundStatement(bs, ConsistencyLevel.EACH_QUORUM);

		verify(bs).setConsistencyLevel(com.datastax.driver.core.ConsistencyLevel.EACH_QUORUM);
		assertThat(boundStatements).containsOnly(bs);
	}

	@Test
	public void should_push_statement_with_consistency_overriden_by_current_level()
			throws Exception
	{
		List<BoundStatement> boundStatements = new ArrayList<BoundStatement>();
		Whitebox.setInternalState(context, "boundStatements", boundStatements);

		context.setWriteConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
		context.pushBoundStatement(bs, ConsistencyLevel.EACH_QUORUM);

		verify(bs).setConsistencyLevel(com.datastax.driver.core.ConsistencyLevel.LOCAL_QUORUM);
		assertThat(boundStatements).containsOnly(bs);
	}

	@Test
	public void should_execute_immediate_with_consistency_level() throws Exception
	{
		ResultSet result = mock(ResultSet.class);
		when(daoContext.execute(query)).thenReturn(result);

		ResultSet actual = context.executeImmediateWithConsistency(query,
				ConsistencyLevel.EACH_QUORUM);

		assertThat(actual).isSameAs(result);
		verify(query).setConsistencyLevel(com.datastax.driver.core.ConsistencyLevel.EACH_QUORUM);
	}

	@Test
	public void should_execute_immediate_with_consistency_level_overriden_by_current_level()
			throws Exception
	{
		ResultSet result = mock(ResultSet.class);
		when(daoContext.execute(query)).thenReturn(result);

		context.setReadConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
		ResultSet actual = context.executeImmediateWithConsistency(query,
				ConsistencyLevel.EACH_QUORUM);

		assertThat(actual).isSameAs(result);
		verify(query).setConsistencyLevel(com.datastax.driver.core.ConsistencyLevel.LOCAL_QUORUM);
	}

	@Test
	public void should_flush() throws Exception
	{
		List<BoundStatement> boundStatements = new ArrayList<BoundStatement>();
		boundStatements.add(bs);
		Whitebox.setInternalState(context, "boundStatements", boundStatements);

		context.flush();

		verify(daoContext).execute(bs);
		assertThat(boundStatements).isEmpty();
	}
}
