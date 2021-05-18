# aspectJ_test

The instructions below are only tested with _IntelliJ Ultimate_.

### Setup

IntelliJ: Make shure AspectJ plugin is actived: Settings -> Plugins

In _Build, Execution, Deployments_ -> _Compiler_  -> _Java Compiler_ 

set _Use compiler_ to _ajc_

set _path to aspectjtools.jar_ either to the jar downloaded by maven or [download aspectJ installer](http://www.eclipse.org/aspectj/downloads.php) and run the jar to install and copy path to aspectjtools.jar.

### How to build:

manually ``mvn clean package``

or just run _Main_ class in IntelliJ

__Note: sometimes when running in IntelliJ/IDEA for some reason the Aspects do not work. In this case recompile and hope it works next time :)__ 

### Run recording of test data:

Setup Classes / Methods to record in ``config.properties``

### replay / regression testing

Run ``RegressionTest1.java``

### resursion / StackOverFlow when serializing obj
## Known issues

ect net which contains loops