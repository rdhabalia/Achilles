package info.archinnov.achilles.entity.manager;

import info.archinnov.achilles.entity.context.AchillesConfigurationContext;
import info.archinnov.achilles.entity.context.DaoContext;
import info.archinnov.achilles.entity.context.ThriftImmediateFlushContext;
import info.archinnov.achilles.entity.context.ThriftPersistenceContext;
import info.archinnov.achilles.entity.context.execution.SafeExecutionContext;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.operations.AchillesEntityRefresher;
import info.archinnov.achilles.entity.operations.ThriftEntityLoader;
import info.archinnov.achilles.entity.operations.ThriftEntityMerger;
import info.archinnov.achilles.entity.operations.ThriftEntityPersister;
import info.archinnov.achilles.entity.type.ConsistencyLevel;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ThriftEntityManager
 * 
 * Thrift-based Entity Manager for Achilles. This entity manager is perfectly thread-safe and
 * 
 * can be used as a singleton. Entity state is stored in proxy object, which is obviously not
 * 
 * thread-safe.
 * 
 * Internally the ThriftEntityManager relies on Hector API for common operations
 * 
 * @author DuyHai DOAN
 * 
 */
public class ThriftEntityManager extends AchillesEntityManager
{
	private static final Logger log = LoggerFactory.getLogger(ThriftEntityManager.class);

	protected final DaoContext daoContext;

	ThriftEntityManager(Map<Class<?>, EntityMeta<?>> entityMetaMap, //
			DaoContext daoContext, //
			AchillesConfigurationContext configContext)
	{
		super(entityMetaMap, configContext);
		this.daoContext = daoContext;
		super.persister = new ThriftEntityPersister();
		super.loader = new ThriftEntityLoader();
		super.merger = new ThriftEntityMerger();
		super.refresher = new AchillesEntityRefresher(super.loader);
	}

	public void persist(final Object entity, ConsistencyLevel writeLevel)
	{
		log.debug("Persisting entity '{}' with write consistency level {}", entity,
				writeLevel.name());
		consistencyPolicy.setCurrentWriteLevel(writeLevel);
		reinitConsistencyLevels(new SafeExecutionContext<Void>()
		{
			@Override
			public Void execute()
			{
				persist(entity);
				return null;
			}
		});
	}

	public <T> T merge(final T entity, ConsistencyLevel writeLevel)
	{
		if (log.isDebugEnabled())
		{
			log.debug("Merging entity '{}' with write consistency level {}",
					proxifier.unproxy(entity), writeLevel.name());
		}
		consistencyPolicy.setCurrentWriteLevel(writeLevel);
		return reinitConsistencyLevels(new SafeExecutionContext<T>()
		{
			@Override
			public T execute()
			{
				return merge(entity);
			}
		});
	}

	public void remove(final Object entity, ConsistencyLevel writeLevel)
	{
		if (log.isDebugEnabled())
		{
			log.debug("Removing entity '{}' with write consistency level {}",
					proxifier.unproxy(entity), writeLevel.name());
		}
		consistencyPolicy.setCurrentWriteLevel(writeLevel);
		reinitConsistencyLevels(new SafeExecutionContext<Void>()
		{
			@Override
			public Void execute()
			{
				remove(entity);
				return null;
			}
		});

	}

	public <T> T find(final Class<T> entityClass, final Object primaryKey,
			ConsistencyLevel readLevel)
	{
		log.debug("Find entity class '{}' with primary key {} and read consistency level {}",
				entityClass, primaryKey, readLevel.name());

		consistencyPolicy.setCurrentReadLevel(readLevel);
		return reinitConsistencyLevels(new SafeExecutionContext<T>()
		{
			@Override
			public T execute()
			{
				return find(entityClass, primaryKey);
			}
		});
	}

	public <T> T getReference(final Class<T> entityClass, final Object primaryKey,
			ConsistencyLevel readLevel)
	{
		log.debug(
				"Get reference for entity class '{}' with primary key {} and read consistency level {}",
				entityClass, primaryKey, readLevel.name());

		consistencyPolicy.setCurrentReadLevel(readLevel);
		return reinitConsistencyLevels(new SafeExecutionContext<T>()
		{
			@Override
			public T execute()
			{
				return find(entityClass, primaryKey);
			}
		});
	}

	public void refresh(final Object entity, ConsistencyLevel readLevel)
	{
		if (log.isDebugEnabled())
		{
			log.debug("Refreshing entity '{}' with read consistency level {}",
					proxifier.unproxy(entity), readLevel.name());
		}
		consistencyPolicy.setCurrentReadLevel(readLevel);
		reinitConsistencyLevels(new SafeExecutionContext<Void>()
		{
			@Override
			public Void execute()
			{
				refresh(entity);
				return null;
			}
		});
	}

	/**
	 * Create a new state-full EntityManager for batch handling <br/>
	 * <br/>
	 * 
	 * <strong>WARNING : This EntityManager is state-full and not thread-safe. In case of exception, you MUST not re-use it but create another one</strong>
	 * 
	 * @return a new state-full EntityManager
	 */
	public ThriftBatchingEntityManager batchingEntityManager()
	{
		return new ThriftBatchingEntityManager(entityMetaMap, daoContext, configContext);
	}

	@SuppressWarnings("unchecked")
	protected <T, ID> ThriftPersistenceContext<ID> initPersistenceContext(Class<T> entityClass,
			ID primaryKey)
	{
		log.trace("Initializing new persistence context for entity class {} and primary key {}",
				entityClass.getCanonicalName(), primaryKey);

		EntityMeta<ID> entityMeta = (EntityMeta<ID>) this.entityMetaMap.get(entityClass);
		return new ThriftPersistenceContext<ID>(entityMeta, configContext, daoContext,
				new ThriftImmediateFlushContext(daoContext, consistencyPolicy), entityClass,
				primaryKey);
	}

	@SuppressWarnings("unchecked")
	protected <ID> ThriftPersistenceContext<ID> initPersistenceContext(Object entity)
	{
		log.trace("Initializing new persistence context for entity {}", entity);

		EntityMeta<ID> entityMeta = (EntityMeta<ID>) this.entityMetaMap.get(proxifier
				.deriveBaseClass(entity));
		return new ThriftPersistenceContext<ID>(entityMeta, configContext, daoContext,
				new ThriftImmediateFlushContext(daoContext, consistencyPolicy), entity);
	}

	private <T> T reinitConsistencyLevels(SafeExecutionContext<T> context)
	{
		try
		{
			return context.execute();
		}
		finally
		{
			consistencyPolicy.reinitCurrentConsistencyLevels();
			consistencyPolicy.reinitDefaultConsistencyLevels();
		}
	}
}
