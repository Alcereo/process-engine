package ru.alcereo.processdsl;

import akka.dispatch.OnComplete;
import akka.dispatch.OnFailure;
import akka.dispatch.OnSuccess;

import java.util.function.Consumer;

public class Utils {

    public static OnFailure failure(Consumer<Throwable> consumer){
        return new OnFailure() {
            @Override
            public void onFailure(Throwable failure) throws Throwable {
                consumer.accept(failure);
            }
        };
    }

    public static <T> OnSuccess<T> success(Consumer<T> consumer){
        return new OnSuccess<T>() {
            @Override
            public void onSuccess(T result) throws Throwable {
                consumer.accept(result);
            }
        };
    }

    public static <V> OnComplete<V> onCompelte(Consumer2<Throwable,V> consumer){
        return new OnComplete<V>() {
            @Override
            public void onComplete(Throwable failure, V success) throws Throwable {
                consumer.execute(failure, success);
            }
        };
    }

    public interface Consumer2<T,V>{
        void execute(T t, V v);
    }
}
