# minijavac [![Build Status](https://travis-ci.org/mj3-16/minijavac.svg?branch=master)](https://travis-ci.org/mj3-16/minijavac)

## Build and run

To run the application, do

```
$ ./gradlew run
```

To build the app, do

```
$ ./gradlew build
```

To run the tests, do

```
$ ./gradlew test
```

To run the benchmarks, do

```
$ ./gradlew jmh
```

You will find the output of the benchmarks in `${project_dir}/build/reports/human.txt` .

## Project file generation

This project is gradle-based, so you can generate project files specific to any
IDE with the appropriate gradle plugin.

For IntelliJ IDEA:

```
$ ./gradlew idea
```

For Eclipse:

```
$ ./gradlew eclipse
```
