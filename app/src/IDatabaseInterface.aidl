// IDatabaseInterface.aidl
package com.screenlake.recorder.services;

interface IDatabaseInterface {
    void saveOcrResult(Integer id, Boolean text, String filePath);
    // Add other methods as needed
}