package com.example.syssweep.shizuku;

interface ISysSweepUserService {
    void destroy() = 16777114;
    int getUid() = 1;
    boolean trimAllCaches() = 2;
    boolean forceStopPackage(String packageName, int userId) = 3;
    int forceStopPackages(in List<String> packageNames, int userId) = 4;
    boolean uninstallPackage(String packageName, int userId) = 5;
    boolean disablePackage(String packageName, int userId) = 6;
    boolean enablePackage(String packageName) = 7;
}
