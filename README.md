# minijavac [![Build Status](https://travis-ci.org/mj3-16/minijavac.svg?branch=master)](https://travis-ci.org/mj3-16/minijavac)

## Build Requirements
- JDK version 8 or higher (uses whatever it finds in $PATH;
  alternatively set $JAVA_HOME to point to the installation directory
  of the JDK)
- Internet connection (required dependencies will be downloaded
  automatically)
- bash (`build` and `run` scripts are written for `sh`, but they in
  turn call automatically generated bash scripts)
- gradle for building this project and jFirm

## Build and run

To build the app, do

```
$ ./build
```

To run the app, do

```
$ ./run
```

To run the test suite (make sure you have initialized all submodules), do

```
$ ./gradlew check
```

## Project file generation

This project is gradle-based, for which common IDEs (read: IntelliJ IDEA) provide project-file generation.
