package aspects;

import aspects.InvocationData;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.MethodSignature;

import javax.sql.rowset.serial.SerialBlob;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Aspect
public class MLogger {
    private final int METHODS_CACHE_SIZE = 1;
    private final int CONSTRUCTORS_CACHE_SIZE = 1;

    private static final String SQL_DROP = "DROP TABLE IF EXISTS INVOCATION_DATA";

    private static final String SQL_CREATE = "CREATE TABLE INVOCATION_DATA"
            + "("
            + " ID serial,"
            + " CLASS_NAME varchar(100) NOT NULL,"
            + " METHOD_NAME varchar(100) NOT NULL,"
            + " INPUT_ARGUMENT_TYPES text[] NOT NULL,"
            + " INPUT_ARGUMENTS text NOT NULL,"
            + " RETURN_TYPE varchar(100) NOT NULL,"
            + " RETURN_VALUE text NOT NULL,"
            + " INVOCATION_TIMESTAMP bigint NOT NULL,"
            + " INVOCATION_TIME bigint NOT NULL,"
            + " ORDER_ID bigint NOT NULL,"
            + " THREAD_ID bigint NOT NULL,"
            + " THREAD_NAME varchar(100) NOT NULL,"
            + " OBJECT_HASH_CODE integer NOT NULL,"
            + " IS_CONSTRUCTOR boolean NOT NULL,"
            + " PRIMARY KEY (ID)"
            + ")";
    private static final String SQL_INSERT = "INSERT INTO INVOCATION_DATA (" +
            "CLASS_NAME," +
            "METHOD_NAME," +
            "INPUT_ARGUMENT_TYPES," +
            "INPUT_ARGUMENTS," +
            "RETURN_TYPE," +
            "RETURN_VALUE," +
            "INVOCATION_TIMESTAMP," +
            "INVOCATION_TIME," +
            "ORDER_ID," +
            "THREAD_ID," +
            "THREAD_NAME," +
            "OBJECT_HASH_CODE," +
            "IS_CONSTRUCTOR" +
            ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";

    Set<String> classes;
    Set<String> methods;
    Connection conn;
    PreparedStatement psInsert;
    MLogger() {
        try (InputStream input = new FileInputStream("config.properties")) {

            Properties prop = new Properties();
            prop.load(input);

            classes = new HashSet<>(Arrays.asList(prop.getProperty("classes").trim().split("[;]")));
            methods = new HashSet<>(Arrays.asList(prop.getProperty("methods").trim().split("[;]")));
            classes.removeIf(String::isEmpty);
            methods.removeIf(String::isEmpty);
            if (classes.isEmpty()) {
                return;
            }
            String url = "jdbc:postgresql://localhost:54320/";
            Properties props = new Properties();
            props.setProperty("user","postgres");
            props.setProperty("password","my_password");
//            props.setProperty("password","example");
//            props.setProperty("ssl","true");
            conn = DriverManager.getConnection(url, props);
            if (conn != null) {
                System.out.println("Connected to the database!");
                Statement statement = conn.createStatement();
                statement.execute(SQL_DROP);
                statement.execute(SQL_CREATE);
                psInsert = conn.prepareStatement(SQL_INSERT);
                conn.setAutoCommit(false);
            } else {
                System.out.println("Failed to make connection!");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    }

    List<InvocationData> methodInvocationRecords = new LinkedList<InvocationData>();
    List<InvocationData> constructorInvocationRecords = new LinkedList<InvocationData>();
//    private Gson gson = new Gson();
    ObjectMapper objectMapper = new ObjectMapper();


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
            long start = System.nanoTime();
            Object result = point.proceed();
            long executionTime = System.nanoTime() - start;

            String[] returnType = ((MethodSignature) point.getSignature()).getReturnType().toString().split(" ");
//            System.out.println("Writing method invocation");
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


    private void writeInvocationRecords(InvocationData invocationData, boolean isConstructor) throws JsonProcessingException {
        final List<InvocationData> invocationRecords = isConstructor ? constructorInvocationRecords : methodInvocationRecords;
        final String recordsFileName = isConstructor ? "constructor_invocation_records.json" : "method_invocation_records.json";

        System.out.println(objectMapper.writeValueAsString(invocationData.inputArgs));
        invocationRecords.add(invocationData);
        if (invocationRecords.size() >= (isConstructor ? CONSTRUCTORS_CACHE_SIZE : METHODS_CACHE_SIZE)) {
            List<InvocationData> tempRecords = new ArrayList<InvocationData>(invocationRecords);
            invocationRecords.clear();
            try {
                for (InvocationData record : tempRecords) {
//                    blob.blob;
                    psInsert.setString(1, record.className);
                    psInsert.setString(2, record.methodName);
                    psInsert.setArray(3, conn.createArrayOf("text", record.inputArgsTypes));
                    psInsert.setString(5, record.returnValueType);
                    psInsert.setString(4, record.inputArgs.length == 0 ? "" : objectMapper.writeValueAsString(record.inputArgs));
                    psInsert.setString(6, objectMapper.writeValueAsString(record.returnValue));
                    psInsert.setLong(7, record.invocationTimeStamp);
                    psInsert.setLong(8, record.invocationTime);
                    psInsert.setLong(9, record.orderId);
                    psInsert.setLong(10, record.threadId);
                    psInsert.setString(11, record.threadName);
                    psInsert.setInt(12, record.objectHashCode);
                    psInsert.setBoolean(13, isConstructor);
                    psInsert.executeUpdate();
//                    psInsert.setb
                }
                conn.commit();

//                FileWriter myWriter = new FileWriter(recordsFileName, true);
//                String jsonData = tempRecords.stream().map(record -> {
//                    try {
//                        return gson.toJson(record);
//                    } catch (Exception e) {
//                        System.out.println(e.toString());
//                        return "";
//                    }
//                }).collect(Collectors.joining(",", "", ","));
//                myWriter.write(jsonData);
//                myWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Before("execution(*.new(..)) && !within(MLogger)")
    public void aroundSecond(JoinPoint point) throws JsonProcessingException{
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
