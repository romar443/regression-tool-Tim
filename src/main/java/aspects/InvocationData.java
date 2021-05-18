package aspects;

public class InvocationData {
    public String className;
    public String methodName;
    public String[] inputArgsTypes;
    public Object[] inputArgs;
    public String returnValueType;
    public Object returnValue;
    public long invocationTimeStamp;
    public long invocationTime;
    public long orderId;
    public long threadId;
    public String threadName;
    public int objectHashCode;

    public InvocationData() {
    }

    public InvocationData(String className,
                          String methodName,
                          String[] inputArgsTypes,
                          Object[] inputArgs,
                          String returnValueType,
                          Object returnValue,
                          long invocationTime,
                          long invocationTimeStamp,
                          long orderId,
                          long threadId,
                          String threadName,
                          int objectHashCode) {
        this.className = className;
        this.methodName = methodName;
        this.inputArgsTypes = inputArgsTypes;
        this.inputArgs = inputArgs;
        this.returnValueType = returnValueType;
        this.returnValue = returnValue;
        this.invocationTime = invocationTime;
        this.invocationTimeStamp = invocationTimeStamp;
        this.orderId = orderId;
        this.threadId = threadId;
        this.threadName = threadName;
        this.objectHashCode = objectHashCode;
    }

}
