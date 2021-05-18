package aspects;

import com.google.gson.Gson;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.MethodSignature;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Aspect
public class MLogger {
    private final int METHODS_CACHE_SIZE = 1;
    private final int CONSTRUCTORS_CACHE_SIZE = 1;

    Set<String> classes;
    Set<String> methods;

    MLogger() {
        try (InputStream input = new FileInputStream("config.properties")) {

            Properties prop = new Properties();
            prop.load(input);

            classes = new HashSet<>(Arrays.asList(prop.getProperty("classes").trim().split("[;]")));
            methods = new HashSet<>(Arrays.asList(prop.getProperty("methods").trim().split("[;]")));
            classes.removeIf(String::isEmpty);
            methods.removeIf(String::isEmpty);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    List<InvocationData> methodInvocationRecords = new LinkedList<InvocationData>();
    List<InvocationData> constructorInvocationRecords = new LinkedList<InvocationData>();
    private Gson gson = new Gson();

    AtomicInteger atomicCounter = new AtomicInteger(0);

    @Around("execution(* *(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {

        final String methodName = MethodSignature.class.cast(point.getSignature()).getMethod().getName();
        final String className = MethodSignature.class.cast(point.getSignature()).getMethod().getDeclaringClass().getName();
        final String packageName = MethodSignature.class.cast(point.getSignature()).getMethod().getDeclaringClass().getPackage().getName();

        if (// record specific class
                (classes.contains("all") || classes.contains(className)) &&
                        (methods.contains("all") || methods.contains(methodName)) &&
                        // we do not want to record ourselves, otherwise recursion happens
                        !methodName.equals("writeInvocationRecords")) {

            System.out.println("--------------------");
            System.out.println("methodName: " + methodName);
            System.out.println("className: " + className);
            System.out.println("packageName: " + packageName);

            long start = System.nanoTime();
            Object result = point.proceed();
            long executionTime = System.nanoTime() - start;

            String[] returnType = ((MethodSignature) point.getSignature()).getReturnType().toString().split(" ");
            System.out.println("Writing method invocation");
            writeInvocationRecords(new InvocationData(
                    className,
                    methodName,
                    Arrays.stream(((MethodSignature) point.getSignature()).getParameterTypes()).map((e) -> e == null ? Object.class.toString() : e.toString()).map((e) -> e.split(" ").length > 1 ? e.split(" ")[1] : e.split(" ")[0]).toArray(String[]::new),
                    point.getArgs(),
                    returnType.length > 1 ? returnType[1] : returnType[0],
                    result,
                    executionTime,
                    start,
                    atomicCounter.getAndAdd(1),
                    Thread.currentThread().getId(),
                    Thread.currentThread().getName(),
                    java.lang.System.identityHashCode(point.getTarget())
            ), false);

            return result;
        } else {
            return point.proceed();
        }
    }


    private void writeInvocationRecords(InvocationData invocationData, boolean isConstructor) {
        final List<InvocationData> invocationRecords = isConstructor ? constructorInvocationRecords : methodInvocationRecords;
        final String recordsFileName = isConstructor ? "constructor_invocation_records.json" : "method_invocation_records.json";

        invocationRecords.add(invocationData);
        if (invocationRecords.size() >= (isConstructor ? CONSTRUCTORS_CACHE_SIZE : METHODS_CACHE_SIZE)) {
            List<InvocationData> tempRecords = new ArrayList<InvocationData>(invocationRecords);
            invocationRecords.clear();
            try {
                FileWriter myWriter = new FileWriter(recordsFileName, true);
                String jsonData = tempRecords.stream().map(record -> {
                    try {
                        return gson.toJson(record);
                    } catch (Exception e) {
                        System.out.println(e.toString());
                        return "";
                    }
                }).collect(Collectors.joining(",", "", ","));
                myWriter.write(jsonData);
                myWriter.close();
            } catch (Exception e) {
                System.out.println("error");
                e.printStackTrace();
            }
        }
    }

    @Before("execution(*.new(..)) && !within(MLogger)")
    public void aroundSecond(JoinPoint point) {
        final String className = ConstructorSignature.class.cast(point.getSignature()).getConstructor().getName();

        if (classes.contains("all") || classes.contains(className)) {
            System.out.println(className);
            final String methodName = ConstructorSignature.class.cast(point.getSignature()).getName();
            long start = System.nanoTime();
            long executionTime = System.nanoTime() - start;
            String[] returnType = methodName.toString().split(" ");

            writeInvocationRecords(new InvocationData(
                    className,
                    methodName,
                    Arrays.stream(((ConstructorSignature) point.getSignature()).getParameterTypes()).map((e) -> e == null ? Object.class.toString() : e.toString()).map((e) -> e.split(" ").length > 1 ? e.split(" ")[1] : e.split(" ")[0]).toArray(String[]::new),
                    point.getArgs(),
                    returnType.length > 1 ? returnType[1] : returnType[0],
                    null,
                    executionTime,
                    start,
                    atomicCounter.getAndAdd(1),
                    Thread.currentThread().getId(),
                    Thread.currentThread().getName(),
                    java.lang.System.identityHashCode(point.getTarget())
            ), true);
        }
    }
}
