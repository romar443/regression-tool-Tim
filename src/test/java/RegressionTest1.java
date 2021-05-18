import aspects.InvocationData;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RegressionTest1 {

    private final Gson gson = new Gson();
    private final String CONSTRUCTORS_FILE_NAME = "constructor_invocation_records.json";
    private final String METHODS_FILE_NAME = "method_invocation_records.json";
    String classForTest;
    boolean executePrivateMethods;

    public RegressionTest1() {
        try (InputStream input = new FileInputStream("config.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            executePrivateMethods = Boolean.parseBoolean(prop.getProperty("executePrivateMethods", "false"));
            classForTest = prop.getProperty("classForTest");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @TestFactory
    public Collection<DynamicTest> secondTest() throws Exception {
        System.out.println("Starting secondTest");
        normalizeJsonFileIfNeeded(CONSTRUCTORS_FILE_NAME);
        normalizeJsonFileIfNeeded(METHODS_FILE_NAME);

        JsonFactory f = new MappingJsonFactory();
        JsonParser constructorsJP = f.createParser(new File(CONSTRUCTORS_FILE_NAME));
        JsonParser methodsJP = f.createParser(new File(METHODS_FILE_NAME));

        ObjectMapper mapper = new ObjectMapper();
        List<InvocationData> constructorsData = mapper.readValue(constructorsJP, new TypeReference<List<InvocationData>>() {
        });
        List<InvocationData> methodsData = mapper.readValue(methodsJP, new TypeReference<List<InvocationData>>() {
        });

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
        Class<?>[] argTypes;
        argTypes = Arrays.stream(invocationData.inputArgsTypes).map((String e) -> {
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
            Object result = method.invoke(objectUnderTesting, invocationData.inputArgs);
            System.out.println("compare: " + gson.toJson(result) + " with " + gson.toJson(invocationData.returnValue));
            return DynamicTest.dynamicTest(String.valueOf(invocationData.orderId),
                    () -> assertEquals(gson.toJson(result), gson.toJson(invocationData.returnValue)));
        } catch (Exception e) {
            System.out.println(invocationData.orderId);
            e.printStackTrace();
            return null;
        }

    }

    private void normalizeJsonFileIfNeeded(String fileName) throws Exception {
        FileInputStream fstream = new FileInputStream(fileName);
        BufferedReader br = new BufferedReader(new InputStreamReader(fstream));

        String temp = br.readLine();
        if (temp.isEmpty() || temp.startsWith("[")) {
            System.out.println(fileName + " JSON file already normalized");
            return;
        }
        BufferedWriter bw = new BufferedWriter(new FileWriter(new File(fileName + "-normalized.json"), true));

        String lastValue = temp;

        if (temp != null) {
            lastValue = "[" + temp;
        }

        while ((temp = br.readLine()) != null) {
            bw.write(lastValue);
            bw.newLine();
            lastValue = temp;
        }

        if (lastValue != null && lastValue.endsWith(",")) {
            lastValue = lastValue.substring(0, lastValue.length() - 1);
            lastValue += "]";
            bw.write(lastValue);
        }
        br.close();
        bw.close();
        File oldFile = new File(fileName);
        oldFile.delete();
        File newFile = new File(fileName + "-normalized.json");
        newFile.renameTo(oldFile);
    }
}