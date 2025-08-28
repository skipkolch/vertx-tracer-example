package vertx.worker.traces;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.vertx.core.Future;

import java.util.function.Consumer;
import java.util.function.Function;


@SuppressWarnings("NotNullNullableValidation")
public final class WithSpan {
    private final Tracer tracer = GlobalOpenTelemetry.get().getTracer(WithSpan.class.getSimpleName());

    private final String spanName;
    private final boolean rootSpan;

    public WithSpan(String spanName, boolean rootSpan) {
        this.spanName = spanName;
        this.rootSpan = rootSpan;
    }

    private static void onSuccess(Span span) {
        span.setStatus(StatusCode.OK);
        span.end();
    }


    private static void onFail(Throwable it, Span span) {
        span.recordException(it);
        span.setStatus(StatusCode.ERROR, it.getMessage());
        span.end();
    }

    public void withSpan(Consumer<Span> runnable) {
        withSpan(span -> {
            runnable.accept(span);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    public <T> T withSpan(Function<Span, T> runnable) {
        final var spanBuilder = tracer.spanBuilder(spanName);
        if (rootSpan) {
            spanBuilder.setNoParent();
        } else {
            spanBuilder.setParent(Context.current());
        }

        final var span = spanBuilder.startSpan();
        try (var ignore = span.makeCurrent()) {

            final var result = runnable.apply(Span.current());

            if (result instanceof Future<?> future) {
                return (T) future.onComplete(__ -> onSuccess(span))
                        .onFailure(it -> onFail(it, span));

            }
            onSuccess(span);
            return result;

        } catch (Throwable throwable) {
            onFail(throwable, span);
            throw throwable;
        }
    }

}