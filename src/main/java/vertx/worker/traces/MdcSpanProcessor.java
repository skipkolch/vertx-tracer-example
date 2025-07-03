package vertx.worker.traces;

import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.MDC;

public class MdcSpanProcessor implements SpanProcessor {

    @Override
    public void onStart(io.opentelemetry.context.Context parentContext, ReadWriteSpan span) {
        SpanContext ctx = span.getSpanContext();
        if (ctx.isValid()) {
            MDC.put("trace_id", ctx.getTraceId());
            MDC.put("span_id", ctx.getSpanId());
            MDC.put("trace_flags", ctx.getTraceFlags().asHex());
        }
    }

    @Override
    public void onEnd(ReadableSpan span) {
        MDC.remove("trace_id");
        MDC.remove("span_id");
        MDC.remove("trace_flags");
    }

    @Override
    public boolean isStartRequired() {
        return true;
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }
}
