/*
 * Copyright (c) 2000-2021 Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * http://oss.oracle.com/licenses/upl.
 */
package com.oracle.coherence.guides.cachestores;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.cache.NonBlockingEntryStore;
import com.tangosol.net.cache.StoreObserver;
import com.tangosol.util.BinaryEntry;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Result;

import java.util.Iterator;
import java.util.Set;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;


/**
 * Entry store for integrating with a database with a non-blocking API.
 *
 * See {@link NonBlockingEntryStore}
 *
 * A BinaryEntry is supplied for optimized handling of entries. In this form
 * cache entries are serialized.
 *
 * The deserialized form can still be obtained (incurring the cost to deserialize)
 * by calling {@code .getValue()} or {@code .getKey()}
 *
 * {@code load()} and {@code store} can return immediately, delegating the end
 * of processing to the {@link StoreObserver#onNext onNext} or {@link StoreObserver#onNext onError}
 * calls for completion.
 */
public class H2R2DBCEntryStore
        implements NonBlockingEntryStore<Long, Person>
    {

    /**
     * Obtain a connection to the database.
     *
     * @return  A connection typed publisher
     */
    private Publisher<? extends Connection> getConnection()
        {
        ConnectionFactoryOptions options =
                ConnectionFactoryOptions.builder()
                        .option(DRIVER, "h2")
                        .option(PROTOCOL, "mem")
                        .option(DATABASE, "///testdb;DB_CLOSE_DELAY=-1")
                        .build();

        ConnectionFactory connectionFactory = ConnectionFactories.get(options);

        return connectionFactory.create();
        }

    /**
     * {@inheritDoc}
     */
    @Override
    public void load(BinaryEntry<Long, Person> binEntry, StoreObserver<Long, Person> observer)
        {
        Long lKey = binEntry.getKey();
        CacheFactory.log("H2R2DBCEntryStore load key: " + lKey);

        databaseRead(binEntry, observer);
        }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadAll(Set<? extends BinaryEntry<Long, Person>> setBinEntries, StoreObserver<Long, Person> observer)
        {
        CacheFactory.log("H2R2DBCEntryStore loadAll");

        for (Iterator iter = setBinEntries.iterator(); iter.hasNext(); )
            {
            BinaryEntry binEntry = (BinaryEntry) iter.next();
            Object oKey = binEntry.getKey();

            databaseRead(binEntry, observer);
            }
        }

    /**
     * {@inheritDoc}
     */
    @Override
    public void store(BinaryEntry<Long, Person> binEntry, StoreObserver<Long, Person> observer)
        {
        CacheFactory.log("H2R2DBCEntryStore store");

        databaseWrite(binEntry, observer);
        }

    /**
     * {@inheritDoc}
     */
    @Override
    public void storeAll(Set<? extends BinaryEntry<Long, Person>> setBinEntries, StoreObserver<Long, Person> observer)
        {
        CacheFactory.log("H2R2DBCEntryStore storeAll");

        for (Iterator iter = setBinEntries.iterator(); iter.hasNext(); )
            {
            BinaryEntry binEntry = (BinaryEntry) iter.next();

            databaseWrite(binEntry, observer);
            }
        }

    /**
     * {@inheritDoc}
     */
    @Override
    public void erase(BinaryEntry<Long, Person> binEntry)
        {
        CacheFactory.log("H2R2DBCEntryStore erase");

        databaseDelete(binEntry.getKey());
        }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eraseAll(Set<? extends BinaryEntry<Long, Person>> setBinEntries)
        {
        CacheFactory.log("H2R2DBCEntryStore eraseAll");

        for (Iterator iter = setBinEntries.iterator(); iter.hasNext(); )
            {
            Long lKey = (long) ((BinaryEntry) iter.next()).getKey();

            databaseDelete(lKey);
            }
        }

    /**
     * Perform the actual database read.
     *
     * @param binEntry  Entry to use to populate database
     * @param observer  Observer for signaling the completion of the operation
     */
    protected void databaseRead(BinaryEntry<Long, Person> binEntry, StoreObserver<Long, Person> observer)
        {
        Long lKey = binEntry.getKey();

        Flux.from(getConnection())
                .flatMap(connection -> connection.createStatement(LOAD_STMT)
                        .bind("$1", lKey)
                        .execute())
                .flatMap(result ->
                                 result.map((row, meta) ->
                                                    (new Person((Long) lKey, (Integer) row.get("age"), (String) row.get("firstname"), (String) row.get("lastname")))
                                 ))
                .collectList()
                .doOnNext(s ->
                          {
                          binEntry.setValue(s.get(0));
                          observer.onNext(binEntry);
                          })
                .doOnError(t ->
                           {
                           if (t instanceof IndexOutOfBoundsException)
                               {
                               CacheFactory.log("Could not find row for key: " + lKey);
                               }
                           else
                               {
                               CacheFactory.log("Error: " + t);
                               }
                           observer.onError(binEntry, new Exception(t));
                           })
                .subscribe();
        }

    /**
     * Perform the actual database read.
     *
     * @param binEntry  Entry to use to populate database
     * @param observer  Observer for signaling the completion of the operation
     */
    protected void databaseWrite(BinaryEntry<Long, Person> binEntry, StoreObserver<Long, Person> observer)
        {
        Long lKey     = binEntry.getKey();
        Person person = binEntry.getValue();

        Flux.from(getConnection())
                .flatMap(connection -> connection.createStatement(STORE_STMT)
                        .bind("$1", lKey)
                        .bind("$2", person.getAge())
                        .bind("$3", person.getFirstname())
                        .bind("$4", person.getLastname())
                        .execute())
                .flatMap(Result::getRowsUpdated)
                .doOnNext(s -> observer.onNext(binEntry))
                .doOnError(t -> observer.onError(binEntry, new Exception(t)))
                .subscribe();
        }

    /**
     * Perform the actual database read.
     *
     * @param lKey  Key of entry to remove from the database and cache
     */
    protected void databaseDelete(Long lKey)
        {
        Flux.from(getConnection())
                .flatMap(connection -> connection.createStatement(DELETE_STMT)
                        .bind("$1", lKey)
                        .execute())
                .flatMap(Result::getRowsUpdated)
                .doOnNext(r -> CacheFactory.log("Rows updated: " + r))
                .doOnError(t -> CacheFactory.log("Error: " + t.toString()))
                .blockLast();
        }

    /**
     * SQL statements
     */
    private static final String LOAD_STMT   = "SELECT AGE, FIRSTNAME, LASTNAME FROM PERSON WHERE id=$1;";
    private static final String STORE_STMT  = "INSERT INTO PERSON VALUES ($1, $2, $3, $4);";
    private static final String DELETE_STMT = "DELETE FROM PERSON WHERE id=$1;";
    }