package ru.smartfridge.exception;

import com.netflix.graphql.types.errors.TypedGraphQLError;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import org.springframework.stereotype.Component;
import ru.smartfridge.contract.exception.ResourceNotFoundException;

import java.util.concurrent.CompletableFuture;

@Component
public class GraphQLExceptionHandler implements DataFetcherExceptionHandler {

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
            DataFetcherExceptionHandlerParameters handlerParameters) {

        Throwable ex = handlerParameters.getException();

        if (ex instanceof ResourceNotFoundException) {
            var error = TypedGraphQLError.newNotFoundBuilder()
                    .message(ex.getMessage())
                    .path(handlerParameters.getPath())
                    .build();

            return CompletableFuture.completedFuture(
                    DataFetcherExceptionHandlerResult.newResult().error(error).build()
            );
        }

        var error = TypedGraphQLError.newInternalErrorBuilder()
                .message("Внутренняя ошибка сервера")
                .path(handlerParameters.getPath())
                .build();

        return CompletableFuture.completedFuture(
                DataFetcherExceptionHandlerResult.newResult().error(error).build()
        );
    }
}