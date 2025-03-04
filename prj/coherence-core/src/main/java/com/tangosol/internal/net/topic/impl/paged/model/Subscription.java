/*
 * Copyright (c) 2000, 2021, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * http://oss.oracle.com/licenses/upl.
 */
package com.tangosol.internal.net.topic.impl.paged.model;

import com.tangosol.internal.net.topic.impl.paged.PagedTopicSubscriber;

import com.tangosol.io.AbstractEvolvable;

import com.tangosol.io.pof.EvolvablePortableObject;
import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.io.pof.PortableObject;

import com.tangosol.net.partition.KeyPartitioningStrategy;

import com.tangosol.util.Filter;
import com.tangosol.util.HashHelper;
import com.tangosol.util.ValueExtractor;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import java.util.function.Function;

import java.util.stream.Collectors;

/**
 * Subscriber group data for a particular cache partition.
 *
 * @author mf  2016.02.22
 * @since Coherence 14.1.1
 */
public class Subscription
    extends AbstractEvolvable
    implements EvolvablePortableObject
    {
    // ----- Constructors ---------------------------------------------------

    /**
     * Empty constructor for serialization.
     */
    public Subscription()
        {
        }

    // ----- TopicPartitionSubscription methods -----------------------

    /**
     * Return the subscriber's head page across all partitions
     *
     * @return the head page
     */
    public long getSubscriptionHead()
        {
        return m_lHeadSubscription;
        }

    /**
     * Set the subscriber's head page across all partitions
     *
     * @param lPage  the head page
     */
    public void setSubscriptionHead(long lPage)
        {
        m_lHeadSubscription = lPage;
        }

    /**
     * Return the subscriber's page within the partition
     *
     * @return the page
     */
    public long getPage()
        {
        return m_lPage;
        }

    /**
     * Set the subscriber's page within the partition
     *
     * @param lPage the page
     */
    public void setPage(long lPage)
        {
        m_lPage = lPage;
        }

    /**
     * Return the read position for this partition's active page.
     *
     * @return the read position
     */
    public int getPosition()
        {
        return m_nPosition;
        }

    /**
     * Set the read position for this partition's active page.
     *
     * @param nPosition the position
     */
    public void setPosition(int nPosition)
        {
        m_nPosition = nPosition;
        }

    /**
     * Return the head {@link PagedPosition} for this partition's active page.
     *
     * @return the head {@link PagedPosition}
     */
    public PagedPosition getHeadPosition()
        {
        return new PagedPosition(m_lPage, m_nPosition);
        }

    /**
     * Set the committed position in the committed page.
     *
     * @param posCommit    the committed position
     * @param posRollback  the position to rollback to on reconnection or channel redistribution
     */
    public void setCommittedPosition(PagedPosition posCommit, PagedPosition posRollback)
        {
        m_posCommitted = posCommit;
        m_posRollback  = posRollback;
        }

    /**
     * Returns the last committed {@link PagedPosition}.
     *
     * @return the last committed {@link PagedPosition}
     */
    public PagedPosition getCommittedPosition()
        {
        return m_posCommitted;
        }

    /**
     * Returns the {@link PagedPosition} the subscription will rollback to.
     *
     * @return the {@link PagedPosition} the subscription will rollback to
     */
    public PagedPosition getRollbackPosition()
        {
        return m_posRollback;
        }

    /**
     * Return the subscription's filter, or null
     *
     * @return the filter
     */
    public Filter<?> getFilter()
        {
        return m_filter;
        }

    /**
     * Set the subscription's filter
     *
     * @param filter the filter
     */
    public void setFilter(Filter<?> filter)
        {
        m_filter = filter;
        }

    /**
     * Return the subscription's converter, or null.
     *
     * @return the converter {@link Function}
     */
    public Function<?, ?> getConverter()
        {
        return m_fnConvert;
        }

    /**
     * Set the subscription's converter {@link Function}.
     *
     * @param fnConvert  the filter
     */
    public void setConverter(Function<?, ?> fnConvert)
        {
        m_fnConvert = fnConvert;
        }

    /**
     * Returns {@code true} if a given subscriber is allocated to this owns the channel,
     * or zero if no subscriber owns the channel.
     *
     * @return the identifier of the subscriber that owns the channel
     */
    public long getOwningSubscriber()
        {
        return m_nOwningSubscriber;
        }

    /**
     * Set the identifier of the subscriber that owns the channel,
     * or zero if no subscriber owns the channel.
     *
     * @param nOwningSubscriber  the identifier of the subscriber that owns the channel
     */
    public void setOwningSubscriber(long nOwningSubscriber)
        {
        if (m_nOwningSubscriber != nOwningSubscriber)
            {
            // the owner has changed so we need to reset the position to the last committed page
            // and the next position after the last committed position
            m_nOwningSubscriber = nOwningSubscriber;
            rollback();
            }
        }

    /**
     * Roll the subscriber back to the last committed position.
     */
    public void rollback()
        {
        m_lPage     = m_posRollback.getPage();
        m_nPosition = m_posRollback.getOffset();
        }

    /**
     * Returns the identifier of the subscriber owning the specified channel
     *
     * @param nChannel  the channel to obtain the owning subscriber identifier for
     *
     * @return  the owning subscriber identifier, or zero if no subscriber owns the channel
     */
    public long getChannelOwner(int nChannel)
        {
        return m_aChannel == null ? m_nOwningSubscriber : m_aChannel[nChannel];
        }

    /**
     * Add a subscriber to the subscription.
     *
     * @param nSubscriber  the unique identifier of the subscriber to add
     * @param cChannel     the number of channels to distribute across the subscribers
     * @param setMember    the set of current member identifiers
     *
     * @return a map of any removed subscribers, keyed by departed member identifier
     */
    public synchronized Map<Integer, Set<Long>> addSubscriber(long nSubscriber, int cChannel, Set<Integer> setMember)
        {
        if (nSubscriber == 0)
            {
            return Collections.emptyMap();
            }

        if (m_setSubscriber == null)
            {
            m_setSubscriber = new TreeSet<>();
            }

        if (m_setSubscriber.add(nSubscriber))
            {
            return refresh(m_setSubscriber, cChannel, setMember);
            }

        return Collections.emptyMap();
        }

    /**
     * Remove a subscriber from the subscription.
     *
     * @param nSubscriber  the unique identifier of the subscriber to remove
     * @param cChannel     the number of channels to distribute across the subscribers
     * @param setMember    the set of current member identifiers
     *
     * @return a map of any removed subscribers, keyed by departed member identifier
     */
    public synchronized Map<Integer, Set<Long>> removeSubscriber(long nSubscriber, int cChannel, Set<Integer> setMember)
        {
        if (nSubscriber == 0)
            {
            return Collections.emptyMap();
            }

        if (m_setSubscriber != null)
            {
            if (m_setSubscriber.remove(nSubscriber))
                {
                Map<Integer, Set<Long>> mapRemoved = refresh(m_setSubscriber, cChannel, setMember);
                int                     nMember    = PagedTopicSubscriber.memberIdFromId(m_nOwningSubscriber);
                mapRemoved.compute(nMember, (key, set) -> ensureSet(nMember, nSubscriber, set));
                return mapRemoved;
                }
            }
        else if (m_nOwningSubscriber == nSubscriber)
            {
            int nMember = PagedTopicSubscriber.memberIdFromId(m_nOwningSubscriber);
            m_nOwningSubscriber = 0;
            return Collections.singletonMap(nMember, Collections.singleton(nSubscriber));
            }

        return Collections.emptyMap();
        }

    /**
     * Returns the set of subscriber identifiers that this subscription knows about.
     *
     * @return  the set of subscriber identifiers that this subscription knows about
     */
    public Set<Long> getSubscribers()
        {
        return m_setSubscriber == null ? Collections.emptySet() : m_setSubscriber;
        }

    /**
     * Returns {@code true} if the subscriber identifier is known to this {@link Subscription}.
     *
     * @param id  the identifier of the subscriber
     *
     * @return {@code true} if the subscriber identifier is known to this {@link Subscription},
     *         otherwise {@code false}
     */
    public boolean hasSubscriber(long id)
        {
        return m_setSubscriber == null ? (m_nOwningSubscriber == id) : m_setSubscriber.contains(id);
        }

    /**
     * Returns the channel allocations.
     *
     * @return the channel allocations
     */
    public String getAllocations()
        {
        Map<Long, List<Integer>> map = new HashMap<>();
        long[]             alChannel = m_aChannel;
        for (int i = 0; i < alChannel.length; i++)
            {
            if (alChannel[i] != 0)
                {
                map.computeIfAbsent(alChannel[i], k -> new ArrayList<>()).add(i);
                }
            }

        if (map.isEmpty())
            {
            return "[all channels unallocated]";
            }

        return map.entrySet().stream()
                .map(e -> e.getValue() + "=" + e.getKey() + "/" + PagedTopicSubscriber.memberIdFromId(e.getKey()))
                .collect(Collectors.joining(", "));
        }

    /**
     * Returns the channels allocated to the specified subscriber.
     *
     * @param nSubscriber  the identifier of the subscriber
     * @param cChannel     the total number of channels
     *
     * @return the channels allocated to the specified subscriber or
     *         an empty list if the subscriber is not allocated any \
     *         channels
     */
    public int[] getChannels(long nSubscriber, int cChannel)
        {
        if (m_aChannel == null || m_aChannel.length == 0)
            {
            if (nSubscriber == m_nOwningSubscriber)
                {
                int[] anChannel = new int[cChannel];
                for (int i = 0; i < cChannel; i++)
                    {
                    anChannel[i] = i;
                    }
                return anChannel;
                }
            return new int[0];
            }


        int   cMatch    = (int) Arrays.stream(m_aChannel).filter(s -> s == nSubscriber).count();
        int[] anChannel = new int[cMatch];
        int   nIndex    = 0;
        for (int i = 0; i < m_aChannel.length; i++)
            {
            if (m_aChannel[i] == nSubscriber)
                {
                anChannel[nIndex++] = i;
                }
            }
        return anChannel;
        }

    /**
     * Returns an immutable {@link Map} of channel allocations.
     *
     * @return an immutable {@link Map} of channel allocations where the key is
     *         a channel number and the value is the owning subscriber identifier
     */
    long[] getChannelAllocations()
        {
        return m_aChannel;
        }

    /**
     * Create the {@link Key} to the {@link Subscription} used as the sync for the subscriptions
     * global information.
     *
     * @param subscriberGroupId  the subscriber group identifier
     * @param nChannel           the channel identifier
     * @param cParts             the number of partitions
     *
     * @return the {@link Key} to the {@link Subscription} used as the sync for the subscriptions
     *         global information
     */
    public static Key createSyncKey(SubscriberGroupId subscriberGroupId, int nChannel, int cParts)
        {
        // we don't just use (0,chan) as that would concentrate extra load on a single partitions when there are many groups
        int nPart = getSyncPartition(subscriberGroupId, nChannel, cParts);
        return new Subscription.Key(nPart, nChannel, subscriberGroupId);
        }

    /**
     * Return the partition used as the sync for the subscriptions global information.
     *
     * @param subscriberGroupId  the subscriber group identifier
     * @param nChannel           the channel identifier
     * @param cParts             the number of partitions
     *
     * @return the partition used as the sync for the subscriptions global information
     */
    public static int getSyncPartition(SubscriberGroupId subscriberGroupId, int nChannel, int cParts)
        {
        return Math.abs((HashHelper.hash(subscriberGroupId.hashCode(), nChannel) % cParts));
        }

    // ----- EvolvablePortableObject interface ------------------------------

    @Override
    public int getImplVersion()
        {
        return DATA_VERSION;
        }

    @Override
    public void readExternal(PofReader in)
        throws IOException
        {
        m_lHeadSubscription = in.readLong(0);
        m_lPage             = in.readLong(1);
        m_nPosition         = in.readInt(2);
        m_filter            = in.readObject(3);
        m_fnConvert         = in.readObject(4);
        m_setSubscriber     = new TreeSet<>();
        if (getDataVersion() >= 2)
            {
            m_nOwningSubscriber = in.readLong(5);
            m_setSubscriber     = in.readCollection(6, m_setSubscriber);
            m_aChannel          = in.readLongArray(7);
            m_posCommitted      = in.readObject(8);
            m_posRollback       = in.readObject(9);
            }
        }

    @Override
    public void writeExternal(PofWriter out)
            throws IOException
        {
        out.writeLong(0, m_lHeadSubscription);
        out.writeLong(1, m_lPage);
        out.writeInt(2, m_nPosition);
        out.writeObject(3, m_filter);
        out.writeObject(4, m_fnConvert);
        out.writeLong(5, m_nOwningSubscriber);
        out.writeCollection(6, m_setSubscriber);
        out.writeLongArray(7, m_aChannel);
        out.writeObject(8, m_posCommitted);
        out.writeObject(9, m_posRollback);
        }

    // ----- Object methods -------------------------------------------------

    @Override
    public String toString()
        {
        return getClass().getSimpleName() + "(head=" + m_lHeadSubscription
            + ", page=" + m_lPage + ", position=" + m_nPosition
            + ", committed=" + (m_posCommitted.getPage() == Page.NULL_PAGE ? "None" : m_posCommitted)
            + ", rollback=" + (m_posRollback.getPage() == Page.NULL_PAGE ? "Unset" : m_posRollback)
            + ", filter=" + m_filter + ", converter=" + m_fnConvert
            + ", owner=" + m_nOwningSubscriber + " subscribers=" + m_setSubscriber + ")";
        }

    // ----- helper methods -------------------------------------------------

    /**
     * Refresh the channel allocations.
     * <p>
     * It is <b>vital</b> that this method always returns a consistent allocation for a given
     * set of subscribers, regardless of the order that those subscribers were added.
     * <p>
     * Any subscriber for a member that is not in the {@code setMember} set will be removed
     * from the allocations.
     *
     * @param setSubscriber  the set of subscribers
     * @param cChannel       the number of channels to allocate
     * @param setMember      the current set of member identifiers
     *
     * @return a map of any removed subscribers, keyed by departed member identifier
     */
    private Map<Integer, Set<Long>> refresh(SortedSet<Long> setSubscriber, int cChannel, Set<Integer> setMember)
        {
        long[]                  aChannel    = new long[cChannel];
        Map<Integer, Set<Long>> mapRemoved  = new TreeMap<>();

        // remove any departed subscribers
        Iterator<Long> it = setSubscriber.iterator();
        while (it.hasNext())
            {
            Long      nId     = it.next();
            int       nMember = PagedTopicSubscriber.memberIdFromId(nId);
            if (!setMember.contains(nMember))
                {
                mapRemoved.compute(nMember, (key, set) -> ensureSet(nMember, nId, set));
                it.remove();
                }
            }

        int cSubscriber = setSubscriber.size();
        if (cSubscriber == 0)
            {
            // we have no subscribers
            Arrays.fill(aChannel, 0L);
            }
        else if (cSubscriber == 1L)
            {
            // there is one subscriber so it gets everything
            Arrays.fill(aChannel, setSubscriber.iterator().next());
            }
        else if (cSubscriber >= cChannel)
            {
            // we have more subscribers than channels (or an equal number)
            // so give one channel to each subscriber starting at the beginning
            // until we run out of channels
            int nChannel = 0;
            for (Long id : setSubscriber)
                {
                aChannel[nChannel++] = id;
                if (nChannel >= cChannel)
                    {
                    break;
                    }
                }
            }
        else
            {
            // we have fewer subscribers than channels
            int nChannel = 0;
            int cAlloc   = cChannel / cSubscriber;  // channels per subscriber, rounded down

            // allocate the required number of channels to the subscriber
            for (Long id : setSubscriber)
                {
                for (int i = 0; i < cAlloc; i++)
                    {
                    aChannel[nChannel++] = id;
                    }
                }

            // assign the remainder round-robin
            if (nChannel < cChannel)
                {
                for (Long id : setSubscriber)
                    {
                    aChannel[nChannel++] = id;
                    if (nChannel >= cChannel)
                        {
                        break;
                        }
                    }
                }
            }
        m_aChannel = aChannel;
        return mapRemoved;
        }

    private Set<Long> ensureSet(Integer ignored, Long nId, Set<Long> setId)
        {
        if (setId == null)
            {
            setId = new HashSet<>();
            }
        setId.add(nId);
        return setId;
        }

    // ----- Key class ------------------------------------------------------

    /**
     * Key for the parent class
     */
    // implementation note: Key does not implement Evolvable
    // because adding fields would affect the "equality"
    // of a key
    public static class Key
            implements KeyPartitioningStrategy.PartitionAwareKey, PortableObject
        {
        // ----- constructors -----------------------------------------------

        /**
         * A default constructor used for serialization.
         */
        public Key()
            {
            }

        /**
         * Create a {@link Key} for the specified partition
         * and subscriber.
         *
         * @param nPartition  the partition id
         * @param nChannel    the channel id
         * @param groupId     the subscriber id
         */
        public Key(int nPartition, int nChannel, SubscriberGroupId groupId)
            {
            m_nPartition = nPartition;
            m_nChannel   = nChannel;
            m_groupId    = groupId;
            }

        // ----- accessor methods -------------------------------------------

        /**
         * Obtain the subscriber that this key is for.
         *
         * @return the subscriber that this key is for
         */
        public SubscriberGroupId getGroupId()
            {
            return m_groupId;
            }

        /**
         * Return the channel id.
         *
         * @return the channel id.
         */
        public int getChannelId()
            {
            return m_nChannel;
            }

        // ----- PartitionAwareKey methods ----------------------------------

        @Override
        public int getPartitionId()
            {
            return m_nPartition;
            }

        // ----- PortableObject methods -------------------------------------

        @Override
        public void readExternal(PofReader in) throws IOException
            {
            m_nPartition = in.readInt(0);
            m_nChannel   = in.readInt(1);
            m_groupId    = in.readObject(2);
            }

        @Override
        public void writeExternal(PofWriter out) throws IOException
            {
            out.writeInt(0, m_nPartition);
            out.writeInt(1, m_nChannel);
            out.writeObject(2, m_groupId);
            }

        // ----- object methods ---------------------------------------------

        @Override
        public boolean equals(Object o)
            {
            if (this == o)
                {
                return true;
                }
            if (o == null || getClass() != o.getClass())
                {
                return false;
                }

            Key that = (Key) o;
            return m_nPartition == that.m_nPartition &&
                   m_nChannel   == that.m_nChannel   &&
                   Objects.equals(m_groupId, that.m_groupId);
            }

        @Override
        public int hashCode()
            {
            int hash = HashHelper.hash(m_nPartition, m_nChannel);

            return HashHelper.hash(m_groupId, hash);
            }

        @Override
        public String toString()
            {
            return "SubscriberPartitionKey(Partition=" + m_nPartition +
                    ", channel=" + m_nChannel +
                    ", subscriberGroup='" + m_groupId + "')";
            }

        // ----- data members -----------------------------------------------

        /**
         * The partition id of this key.
         */
        private int m_nPartition;

        /**
         * The channel id.
         */
        private int m_nChannel;

        /**
         * The subscriber that this key is for.
         */
        private SubscriberGroupId m_groupId;
        }

    // ----- inner class HeadExtractor --------------------------------------

    /**
     * A {@link ValueExtractor} that can extract the head position from
     * a {@link Subscription} for a given subscriber identifier.
     */
    public static class HeadExtractor
            implements ValueExtractor<Subscription, PagedPosition>, PortableObject
        {
        /**
         * Default constructor for serialization.
         */
        @SuppressWarnings("unused")
        public HeadExtractor()
            {
            }

        /**
         * Create a head extractor for the specified subscriber.
         *
         * @param nSubscriberId  the subscriber identifier
         */
        public HeadExtractor(long nSubscriberId)
            {
            m_nSubscriberId = nSubscriberId;
            }

        @Override
        public PagedPosition extract(Subscription subscription)
            {
            PagedPosition position;
            if (subscription.getOwningSubscriber() == m_nSubscriberId)
                {
                position = subscription.getHeadPosition();
                }
            else
                {
                position = subscription.getRollbackPosition();
                if (position.getPage() == Page.EMPTY)
                    {
                    position = subscription.getHeadPosition();
                    }
                }

            if (position.getOffset() == Integer.MAX_VALUE && position.getPage() != Page.EMPTY)
                {
                position = new PagedPosition(position.getPage() + 1, 0);
                }

            return position.getPage() == Page.EMPTY ? null : position;
            }

        // ----- PortableObject methods -------------------------------------

        @Override
        public void readExternal(PofReader in) throws IOException
            {
            m_nSubscriberId = in.readLong(0);
            }

        @Override
        public void writeExternal(PofWriter out) throws IOException
            {
            out.writeLong(0, m_nSubscriberId);
            }

        // ----- data members -----------------------------------------------

        /**
         * The subscriber identifier.
         */
        private long m_nSubscriberId;
        }

    // ----- constants ------------------------------------------------------

    /**
     * {@link EvolvablePortableObject} data version of this class.
     */
    public static final int DATA_VERSION = 2;

    // ----- data members ---------------------------------------------------

    /**
     * The head page across all partitions for this subscriber.
     *
     * Note: this value is only maintained within a single well known partition once the subscription has been initialized.
     */
    private long m_lHeadSubscription = Page.NULL_PAGE;

    /**
     * The page of interest to the subscriber within this partition.
     */
    private long m_lPage = Page.NULL_PAGE;

    /**
     * The read position within the active page, or Integer.MAX_VALUE when the current page is exhausted, but another has
     * yet to arrive for this partition.
     */
    private int m_nPosition;

    /**
     * The last committed position or a position with a {@link Page#NULL_PAGE} page if there have
     * been no commits.
     */
    private PagedPosition m_posCommitted = PagedPosition.NULL_POSITION;

    /**
     * The position to rollback to on reconnection.
     * This is typically the position after the committed page position, but could be a
     * {@link Page#NULL_PAGE} if the next page is unknown.
     */
    private PagedPosition m_posRollback = PagedPosition.NULL_POSITION;

    /**
     * The subscriber filter, or null.
     */
    private Filter<?> m_filter;

    /**
     * The optional converter to use to convert the value before returning to it the subscriber.
     */
    private Function<?, ?> m_fnConvert;

    /**
     * The subscriber owning this channel.
     */
    private long m_nOwningSubscriber;

    /**
     * The set of subscriber ids.
     * <p>
     * This is a sorted set to ensure that subscribers are always iterated over in a consistent order.
     * <p>
     * This set is typically only populated for channel zero.
     */
    private SortedSet<Long> m_setSubscriber;

    /**
     * The an array of subscriber identifiers assigned to channels, the channel number being the index into the array.
     * <p>
     * This set is typically only populated for channel zero.
     */
    private long[] m_aChannel;
    }
