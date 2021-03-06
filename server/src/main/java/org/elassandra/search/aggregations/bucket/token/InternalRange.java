/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elassandra.search.aggregations.bucket.token;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.InternalAggregations;
import org.elasticsearch.search.aggregations.InternalMultiBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.range.Range;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.elasticsearch.search.aggregations.support.ValueType;
import org.elasticsearch.search.aggregations.support.ValuesSourceType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *
 */
public class InternalRange<B extends InternalRange.Bucket, R extends InternalRange<B, R>> extends InternalMultiBucketAggregation<R, B>
        implements Range {
    static final Factory FACTORY = new Factory();

    public static class Bucket extends InternalMultiBucketAggregation.InternalBucket implements Range.Bucket {

        protected final transient boolean keyed;
        protected final transient DocValueFormat format;
        protected long from;
        protected long to;
        private long docCount;
        InternalAggregations aggregations;
        private String key;

        public Bucket(boolean keyed, DocValueFormat formatter) {
            this.keyed = keyed;
            this.format = formatter;
        }

        public Bucket(String key, long from, long to, long docCount, InternalAggregations aggregations, boolean keyed,
                DocValueFormat formatter) {
            this(keyed, formatter);
            this.key = key != null ? key : generateKey(from, to, formatter);
            this.from = from;
            this.to = to;
            this.docCount = docCount;
            this.aggregations = aggregations;
        }

        @Override
        public String getKey() {
            return getKeyAsString();
        }

        @Override
        public String getKeyAsString() {
            return key;
        }

        @Override
        public Object getFrom() {
            return from;
        }

        @Override
        public Object getTo() {
            return to;
        }

        public boolean getKeyed() {
            return keyed;
        }

        public DocValueFormat getFormat() {
            return format;
        }

        @Override
        public String getFromAsString() {
            if (Double.isInfinite(from)) {
                return null;
            } else {
                return format.format(from).toString();
            }
        }

        @Override
        public String getToAsString() {
            if (Double.isInfinite(to)) {
                return null;
            } else {
                return format.format(to).toString();
            }
        }

        @Override
        public long getDocCount() {
            return docCount;
        }

        @Override
        public Aggregations getAggregations() {
            return aggregations;
        }

        protected Factory<? extends Bucket, ?> getFactory() {
            return FACTORY;
        }

        Bucket reduce(List<Bucket> ranges, ReduceContext context) {
            long docCount = 0;
            List<InternalAggregations> aggregationsList = new ArrayList<>(ranges.size());
            for (Bucket range : ranges) {
                docCount += range.docCount;
                aggregationsList.add(range.aggregations);
            }
            final InternalAggregations aggs = InternalAggregations.reduce(aggregationsList, context);
            return getFactory().createBucket(key, from, to, docCount, aggs, keyed, format);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            if (keyed) {
                builder.startObject(key);
            } else {
                builder.startObject();
                builder.field(CommonFields.KEY.getPreferredName(), key);
            }
            if (!Double.isInfinite(from)) {
                builder.field(CommonFields.FROM.getPreferredName(), from);
                if (format != DocValueFormat.RAW) {
                    builder.field(CommonFields.FROM_AS_STRING.getPreferredName(), format.format(from));
                }
            }
            if (!Double.isInfinite(to)) {
                builder.field(CommonFields.TO.getPreferredName(), to);
                if (format != DocValueFormat.RAW) {
                    builder.field(CommonFields.TO_AS_STRING.getPreferredName(), format.format(to));
                }
            }
            builder.field(CommonFields.DOC_COUNT.getPreferredName(), docCount);
            aggregations.toXContentInternal(builder, params);
            builder.endObject();
            return builder;
        }

        protected String generateKey(double from, double to, DocValueFormat formatter) {
            StringBuilder sb = new StringBuilder();
            sb.append(Double.isInfinite(from) ? "*" : formatter.format(from));
            sb.append("-");
            sb.append(Double.isInfinite(to) ? "*" : formatter.format(to));
            return sb.toString();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
        }
    }

    public static class Factory<B extends Bucket, R extends InternalRange<B, R>> {
        public ValuesSourceType getValueSourceType() {
            return ValuesSourceType.NUMERIC;
        }

        public ValueType getValueType() {
            return ValueType.NUMERIC;
        }

        @SuppressWarnings("unchecked")
        public R create(String name, List<B> ranges, DocValueFormat formatter, boolean keyed, List<PipelineAggregator> pipelineAggregators,
                Map<String, Object> metaData) {
            return (R) new InternalRange<B, R>(name, ranges, formatter, keyed, pipelineAggregators, metaData);
        }

        @SuppressWarnings("unchecked")
        public B createBucket(String key, long from, long to, long docCount, InternalAggregations aggregations, boolean keyed,
                DocValueFormat formatter) {
            return (B) new Bucket(key, from, to, docCount, aggregations, keyed, formatter);
        }

        @SuppressWarnings("unchecked")
        public R create(List<B> ranges, R prototype) {
            return (R) new InternalRange<B, R>(prototype.name, ranges, prototype.format, prototype.keyed, prototype.pipelineAggregators(),
                    prototype.metaData);
        }

        @SuppressWarnings("unchecked")
        public B createBucket(InternalAggregations aggregations, B prototype) {
            return (B) new Bucket(prototype.getKey(), prototype.from, prototype.to, prototype.getDocCount(), aggregations, prototype.keyed,
                    prototype.format);
        }
    }

    private List<B> ranges;
    protected DocValueFormat format;
    protected boolean keyed;

    public InternalRange(String name, List<B> ranges, DocValueFormat format, boolean keyed,
            List<PipelineAggregator> pipelineAggregators,
            Map<String, Object> metaData) {
        super(name, pipelineAggregators, metaData);
        this.ranges = ranges;
        this.format = format;
        this.keyed = keyed;
    }

    /**
     * Read from a stream.
     */
    public InternalRange(StreamInput in) throws IOException {
        super(in);
        format = in.readNamedWriteable(DocValueFormat.class);
        keyed = in.readBoolean();
        int size = in.readVInt();
        List<B> ranges = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String key = in.readOptionalString();
            ranges.add(getFactory().createBucket(key, in.readLong(), in.readLong(), in.readVLong(),
                    InternalAggregations.readAggregations(in), keyed, format));
        }
        this.ranges = ranges;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(format);
        out.writeBoolean(keyed);
        out.writeVInt(ranges.size());
        for (B bucket : ranges) {
            out.writeOptionalString(((Bucket) bucket).key);
            out.writeDouble(((Bucket) bucket).from);
            out.writeDouble(((Bucket) bucket).to);
            out.writeVLong(((Bucket) bucket).docCount);
            bucket.aggregations.writeTo(out);
        }
    }

    @Override
    public String getWriteableName() {
        return TokenRangeAggregationBuilder.NAME;
    }

    @Override
    public List<B> getBuckets() {
        return ranges;
    }

    public Factory<B, R> getFactory() {
        return FACTORY;
    }

    @SuppressWarnings("unchecked")
    @Override
    public R create(List<B> buckets) {
        return getFactory().create(buckets, (R) this);
    }

    @Override
    public B createBucket(InternalAggregations aggregations, B prototype) {
        return getFactory().createBucket(aggregations, prototype);
    }

    @SuppressWarnings("unchecked")
    @Override
    public InternalAggregation doReduce(List<InternalAggregation> aggregations, ReduceContext reduceContext) {
        List<Bucket>[] rangeList = new List[ranges.size()];
        for (int i = 0; i < rangeList.length; ++i) {
            rangeList[i] = new ArrayList<>();
        }
        for (InternalAggregation aggregation : aggregations) {
            InternalRange<B, R> ranges = (InternalRange<B, R>) aggregation;
            int i = 0;
            for (Bucket range : ranges.ranges) {
                rangeList[i++].add(range);
            }
        }

        final List<B> ranges = new ArrayList<>();
        for (int i = 0; i < this.ranges.size(); ++i) {
            ranges.add((B) rangeList[i].get(0).reduce(rangeList[i], reduceContext));
        }
        return getFactory().create(name, ranges, format, keyed, pipelineAggregators(), getMetaData());
    }

    @Override
    public XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
        if (keyed) {
            builder.startObject(CommonFields.BUCKETS.getPreferredName());
        } else {
            builder.startArray(CommonFields.BUCKETS.getPreferredName());
        }
        for (B range : ranges) {
            range.toXContent(builder, params);
        }
        if (keyed) {
            builder.endObject();
        } else {
            builder.endArray();
        }
        return builder;
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(ranges, format, keyed);
    }

    @Override
    protected boolean doEquals(Object obj) {
        InternalRange<?,?> that = (InternalRange<?,?>) obj;
        return Objects.equals(ranges, that.ranges)
                && Objects.equals(format, that.format)
                && Objects.equals(keyed, that.keyed);
    }

}
