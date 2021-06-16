import aspects.InvocationData;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RegressionTest1 {

    ObjectMapper objectMapper = new ObjectMapper();
    String classForTest;
    boolean executePrivateMethods;
    Connection conn;

    private static final String SQL_SELECT = "SELECT * FROM INVOCATION_DATA ORDER BY ORDER_ID";

    public RegressionTest1() {
        try (InputStream input = new FileInputStream("config.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            executePrivateMethods = Boolean.parseBoolean(prop.getProperty("executePrivateMethods", "false"));
            classForTest = prop.getProperty("classForTest");
            // psql --username=postgres --password=my_password
            String url = "jdbc:postgresql://localhost:54320/";
            Properties props = new Properties();
            props.setProperty("user","postgres");
            props.setProperty("password","my_password");
            conn = DriverManager.getConnection(url, props);
            if (conn != null) {
                System.out.println("Connected to the database!");
                conn.setAutoCommit(false);
            } else {
                System.out.println("Failed to make connection!");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @TestFactory
    public Collection<DynamicTest> secondTest() throws Exception {
        System.out.println("Starting secondTest");

        List<InvocationData> constructorsData = new LinkedList<InvocationData>();
        List<InvocationData> methodsData = new LinkedList<InvocationData>();

        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery(SQL_SELECT);
        while (rs.next()) {
            Object[] inputArgs;
            if (rs.getString(5).isEmpty()) {
                inputArgs = new Object[0];
            } else {
                Class<?>[] argTypes = getArgTypes((String[])rs.getArray(4).getArray());
                inputArgs = objectMapper.readValue(rs.getString(5), Object[].class);
//                objectMapper.readTree(rs.getString(5)).
//                inputArgs = objectMapper.readTree(rs.getString(5)).iterator().forEachRemaining((jsonNode -> objectMapper.treeToValue(jsonNode, argTypes)));
            }
            InvocationData invocationData = new InvocationData(
                    rs.getString(2),
                    rs.getString(3),
                    (String[])rs.getArray(4).getArray(),
                    inputArgs,
                    rs.getString(6),
                    objectMapper.readValue(rs.getString(7), Object.class),
                    rs.getLong(8),
                    rs.getLong(9),
                    rs.getLong(10),
                    rs.getLong(11),
                    rs.getString(12),
                    rs.getInt(13)
                    );
            if (rs.getBoolean(14)) {
                constructorsData.add(invocationData);
            } else {
                methodsData.add(invocationData);
            }
        }
        st.close();
        rs.close();
        conn.close();

        Class<?> classUnderTesting = Class.forName(classForTest);

        Collection<DynamicTest> testCases = new LinkedList<DynamicTest>();

        for (InvocationData constructorData : constructorsData) {
            Class<?>[] constructorArgTypes = Arrays.stream(constructorData.inputArgsTypes).map((String e) -> {
                try {
                    if (e.equals("boolean")) {
                        return boolean.class;
                    }

                    if (e.equals("int")) {
                        return int.class;
                    }

                    if (e.equals("double")) {
                        return double.class;
                    }
                    if (e.equals("long")) {
                        return long.class;
                    }

                    return Class.forName(e);
                } catch (Exception classNotFoundException) {
                    classNotFoundException.printStackTrace();
                    return Object.class;
                }
            }).collect(Collectors.toList()).toArray(new Class[0]);


            Constructor<?> ctor = classUnderTesting.getConstructor(constructorArgTypes);
            Object objectUnderTesting = ctor.newInstance(constructorData.inputArgs);

            List<InvocationData> currentObjectMethodsInvocationData = methodsData.stream()
                    .filter(e -> e.objectHashCode == constructorData.objectHashCode).sorted((o1, o2) -> {
                        return (int) (o1.orderId - o2.orderId);
                    }).collect(Collectors.toList());

            for (InvocationData invocationData : currentObjectMethodsInvocationData) {
                if (invocationData.className.equals(classForTest)
                ) {
                    DynamicTest dynamicTest = testMethod(invocationData, objectUnderTesting);
                    if (dynamicTest != null) {
                        testCases.add(dynamicTest);
                    }
                }
            }
        }
        return testCases;
    }

    private DynamicTest testMethod(InvocationData invocationData, Object objectUnderTesting) {

        Class<?>[] argTypes = getArgTypes(invocationData.inputArgsTypes);

        Method method;

        try {
            String[] splitMethodName = invocationData.methodName.split("\\.");

            if (executePrivateMethods) {
                method = objectUnderTesting.getClass().getMethod(
                        splitMethodName[splitMethodName.length - 1],
                        argTypes);
            } else {
                method = objectUnderTesting.getClass().getDeclaredMethod(
                        splitMethodName[splitMethodName.length - 1],
                        argTypes);
            }

        } catch (NoSuchMethodException e) {
            System.out.println(e.getMessage());
            return null;
        }

        try {
            method.setAccessible(true);
            Object[] castedInputArgs = new Object[invocationData.inputArgs.length];

            // Here we have an array of read objects inside invocationData.inputArgs and we need to cast it do actual data types somehow
            for(int i = 0; i < invocationData.inputArgs.length; i++) {
//                castedInputArgs[i] = argTypes[i].cast(invocationData.inputArgs[i]);
                castedInputArgs[i] = objectMapper.readValue(objectMapper.writeValueAsString(invocationData.inputArgs[i]), argTypes[i]);
            }

            Object result = method.invoke(objectUnderTesting, castedInputArgs);
            return DynamicTest.dynamicTest(String.valueOf(invocationData.orderId),
                    () -> assertEquals(objectMapper.writeValueAsString(result), objectMapper.writeValueAsString(invocationData.returnValue)));
        } catch (Exception e) {
            System.out.println(invocationData.orderId);
            e.printStackTrace();
            return null;
        }

    }

    Class<?> getType(String e) {
        try {
            if (e.equals("boolean")) {
                return boolean.class;
            }

            if (e.equals("int")) {
                return int.class;
            }

            if (e.equals("double")) {
                return double.class;
            }
            if (e.equals("long")) {
                return long.class;
            }

            return Class.forName(e);
        } catch (Exception classNotFoundException) {
            classNotFoundException.printStackTrace();
            return Object.class;
        }
    }

    private Class<?>[] getArgTypes(String[] inputArgsTypes) {
        return Arrays.stream(inputArgsTypes).map((String e) -> {
            try {
                if (e.equals("boolean")) {
                    return boolean.class;
                }

                if (e.equals("int")) {
                    return int.class;
                }

                if (e.equals("double")) {
                    return double.class;
                }

                if (e.equals("long")) {
                    return long.class;
                }

                return Class.forName(e);
            } catch (Exception classNotFoundException) {
                classNotFoundException.printStackTrace();
                return Object.class;
            }
        }).collect(Collectors.toList()).toArray(new Class[0]);
    }
}