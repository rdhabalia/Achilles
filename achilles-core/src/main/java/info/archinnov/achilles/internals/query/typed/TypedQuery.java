/*
 * Copyright (C) 2012-2016 DuyHai DOAN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.archinnov.achilles.internals.query.typed;

import static info.archinnov.achilles.internals.query.typed.TypedQueryValidator.validateCorrectTableName;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;

import info.archinnov.achilles.internals.metamodel.AbstractEntityProperty;
import info.archinnov.achilles.internals.options.Options;
import info.archinnov.achilles.internals.query.StatementTypeAware;
import info.archinnov.achilles.internals.query.action.SelectAction;
import info.archinnov.achilles.internals.runtime.RuntimeEngine;
import info.archinnov.achilles.internals.statements.BoundStatementWrapper;
import info.archinnov.achilles.internals.statements.StatementWrapper;
import info.archinnov.achilles.internals.types.EntityIteratorWrapper;
import info.archinnov.achilles.internals.types.TypedMapIteratorWrapper;
import info.archinnov.achilles.type.TypedMap;
import info.archinnov.achilles.type.interceptor.Event;
import info.archinnov.achilles.type.tuples.Tuple2;

/**
 * Typed query
 */
public class TypedQuery<ENTITY> implements SelectAction<ENTITY>, StatementTypeAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(TypedQuery.class);

    private final RuntimeEngine rte;
    private final AbstractEntityProperty<ENTITY> meta;
    private final BoundStatement boundStatement;
    private final Object[] encodedBoundValues;
    private final Options options = new Options();


    public TypedQuery(RuntimeEngine rte, AbstractEntityProperty<ENTITY> meta, BoundStatement boundStatement, Object[] encodedBoundValues) {
        this.rte = rte;
        this.meta = meta;
        this.boundStatement = boundStatement;
        this.encodedBoundValues = encodedBoundValues;
        validateCorrectTableName(boundStatement.preparedStatement().getQueryString().toLowerCase(), meta);
    }

    /**
     * Add the given list of async listeners on the {@link com.datastax.driver.core.ResultSet} object.
     * Example of usage:
     * <pre class="code"><code class="java">

     * .withResultSetAsyncListeners(Arrays.asList(resultSet -> {
     * //Do something with the resultSet object here
     * }))

     * </code></pre>

     * Remark: <strong>it is not allowed to consume the ResultSet values. It is strongly advised to read only meta data</strong>
     */
    public TypedQuery<ENTITY> withResultSetAsyncListeners(List<Function<ResultSet, ResultSet>> resultSetAsyncListeners) {
        this.options.setResultSetAsyncListeners(Optional.of(resultSetAsyncListeners));
        return this;
    }

    /**
     * Add the given async listener on the {@link com.datastax.driver.core.ResultSet} object.
     * Example of usage:
     * <pre class="code"><code class="java">

     * .withResultSetAsyncListener(resultSet -> {
     * //Do something with the resultSet object here
     * })

     * </code></pre>

     * Remark: <strong>it is not allowed to consume the ResultSet values. It is strongly advised to read only meta data</strong>
     */
    public TypedQuery<ENTITY> withResultSetAsyncListener(Function<ResultSet, ResultSet> resultSetAsyncListener) {
        this.options.setResultSetAsyncListeners(Optional.of(asList(resultSetAsyncListener)));
        return this;
    }

    /**
     * Add the given list of async listeners on the {@link com.datastax.driver.core.Row} object.
     * Example of usage:
     * <pre class="code"><code class="java">

     * .withRowAsyncListeners(Arrays.asList(row -> {
     * //Do something with the row object here
     * }))

     * </code></pre>

     * Remark: <strong>You can inspect and read values from the row object</strong>
     */
    public TypedQuery<ENTITY> withRowAsyncListeners(List<Function<Row, Row>> rowAsyncListeners) {
        this.options.setRowAsyncListeners(Optional.of(rowAsyncListeners));
        return this;
    }

    /**
     * Add the given async listener on the {@link com.datastax.driver.core.Row} object.
     * Example of usage:
     * <pre class="code"><code class="java">

     * .withRowAsyncListener(row -> {
     * //Do something with the row object here
     * })

     * </code></pre>

     * Remark: <strong>You can inspect and read values from the row object</strong>
     */
    public TypedQuery<ENTITY> withRowAsyncListener(Function<Row, Row> rowAsyncListener) {
        this.options.setRowAsyncListeners(Optional.of(asList(rowAsyncListener)));
        return this;
    }

    /**
     * Execute the typed query and return an iterator of entities
     *
     * @return Iterator&lt;ENTITY&gt;
     */
    @Override
    public Iterator<ENTITY> iterator() {

        StatementWrapper statementWrapper = new BoundStatementWrapper(getOperationType(boundStatement), meta,
                boundStatement, encodedBoundValues);

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(String.format("Generate iterator for typed query : %s",
                    statementWrapper.getBoundStatement().preparedStatement().getQueryString()));
        }

        CompletableFuture<ResultSet> futureRS = rte.execute(statementWrapper);
        return new EntityIteratorWrapper<>(futureRS, meta, statementWrapper, options);
    }

    /**
     * Execute the typed query and return an iterator of entities
     *
     * @return Iterator&lt;ENTITY&gt;
     */
    @Override
    public Tuple2<Iterator<ENTITY>, ExecutionInfo> iteratorWithExecutionInfo() {
        EntityIteratorWrapper iterator = (EntityIteratorWrapper) this.iterator();
        return Tuple2.of(iterator, iterator.getExecutionInfo());
    }

    @Override
    public Iterator<TypedMap> typedMapIterator() {
        StatementWrapper statementWrapper = new BoundStatementWrapper(getOperationType(boundStatement), meta,
                boundStatement, encodedBoundValues);

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(String.format("Generate iterator for typed query : %s",
                    statementWrapper.getBoundStatement().preparedStatement().getQueryString()));
        }

        CompletableFuture<ResultSet> futureRS = rte.execute(statementWrapper);

        return new TypedMapIteratorWrapper(futureRS, statementWrapper, options);
    }

    @Override
    public Tuple2<Iterator<TypedMap>, ExecutionInfo> typedMapIteratorWithExecutionInfo() {
        TypedMapIteratorWrapper iterator = (TypedMapIteratorWrapper) this.typedMapIterator();
        return Tuple2.of(iterator, iterator.getExecutionInfo());
    }

    /**
     * Execute the typed query asynchronously and return a list of entities with execution info
     *
     * @return CompletableFuture&lt;Tuple2&lt;List&lt;ENTITY&gt;, ExecutionInfo&gt;&gt;
     */
    public CompletableFuture<Tuple2<List<ENTITY>, ExecutionInfo>> getListAsyncWithStats() {

        StatementWrapper statementWrapper = new BoundStatementWrapper(getOperationType(boundStatement), meta,
                boundStatement, encodedBoundValues);

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(String.format("Select async with execution info : %s",
                    statementWrapper.getBoundStatement().preparedStatement().getQueryString()));
        }

        CompletableFuture<ResultSet> futureRS = rte.execute(statementWrapper);

        return futureRS
                .thenApply(options::resultSetAsyncListener)
                .thenApply(statementWrapper::logReturnResults)
                .thenApply(statementWrapper::logTrace)
                .thenApply(rs -> Tuple2.of(rs
                                .all()
                                .stream()
                                .map(row -> {
                                    options.rowAsyncListener(row);
                                    return meta.createEntityFrom(row);
                                })
                                .collect(toList()),
                        rs.getExecutionInfo()))
                .thenApply(tuple2 -> {
                    for (ENTITY entity : tuple2._1()) {
                        meta.triggerInterceptorsForEvent(Event.POST_LOAD, entity);
                    }
                    return tuple2;
                });
    }
}
