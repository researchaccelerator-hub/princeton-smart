#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_screenlake_data_repository_NativeLib_getAmplifyConfigurationJson(JNIEnv *env, jobject thiz) {
// Define your JSON string
    const char* jsonConfig = R"JSON(
{
    "UserAgent": "aws-amplify-cli/2.0",
    "Version": "1.0",
    "api": {
        "plugins": {
            "awsAPIPlugin": {
                "screenlakenwest1": {
                    "endpointType": "GraphQL",
                    "endpoint": "https://wovp7xl2zbe4lmqsjn77w2o5im.appsync-api.us-west-1.amazonaws.com/graphql",
                    "region": "us-west-1",
                    "authorizationType": "API_KEY",
                    "apiKey": "da2-cn6v7ofyzva3jg7z6npo4cwt54"
                },
                "serverlessrepo-screenlake-internal-data-gateway": {
                    "endpointType": "REST",
                    "endpoint": "https://eqjfomhbw5.execute-api.us-west-1.amazonaws.com/dev",
                    "region": "us-west-1",
                    "authorizationType": "AMAZON_COGNITO_USER_POOLS"
                },
                "metrics": {
                    "endpointType": "REST",
                    "endpoint": "https://gvl3lq80hj.execute-api.us-west-1.amazonaws.com/dev",
                    "region": "us-west-1",
                    "authorizationType": "AMAZON_COGNITO_USER_POOLS"
                }
            }
        }
    },
    "storage": {
        "plugins": {
            "awsS3StoragePlugin": {
                "bucket": "screenlake-zip-dev115119-dev",
                "region": "us-west-1"
            }
        }
    },
    "auth": {
        "plugins": {
            "awsCognitoAuthPlugin": {
                "UserAgent": "aws-amplify-cli/0.1.0",
                "Version": "0.1.0",
                "IdentityManager": {
                    "Default": {}
                },
                "CredentialsProvider": {
                    "CognitoIdentity": {
                        "Default": {
                            "PoolId": "us-west-1:6c43caeb-8d52-465d-9005-cdc21332d345",
                            "Region": "us-west-1"
                        }
                    }
                },
                "CognitoUserPool": {
                    "Default": {
                        "PoolId": "us-west-1_6MwAIOy68",
                        "AppClientId": "28qavv8ofqkd1vb076lbm5v2hd",
                        "Region": "us-west-1"
                    }
                },
                "Auth": {
                    "Default": {
                        "authenticationFlowType": "USER_SRP_AUTH",
                        "socialProviders": [],
                        "usernameAttributes": [
                            "EMAIL"
                        ],
                        "signupAttributes": [],
                        "passwordProtectionSettings": {
                            "passwordPolicyMinLength": 8,
                            "passwordPolicyCharacters": [
                                "REQUIRES_LOWERCASE",
                                "REQUIRES_UPPERCASE",
                                "REQUIRES_NUMBERS",
                                "REQUIRES_SYMBOLS"
                            ]
                        },
                        "mfaConfiguration": "OFF",
                        "mfaTypes": [],
                        "verificationMechanisms": [
                            "EMAIL"
                        ]
                    }
                }
            }
        }
    }
}
    )JSON";


// Return the JSON string as a Java String
    return env->NewStringUTF(jsonConfig);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_screenlake_data_repository_NativeLib_getAWSConfigurationJson(JNIEnv *env, jobject thiz) {
// Define your JSON string
const char* jsonConfig = R"JSON(
{
    "UserAgent": "aws-amplify-cli/0.1.0",
    "Version": "0.1.0",
    "IdentityManager": {
        "Default": {}
    },
    "CredentialsProvider": {
        "CognitoIdentity": {
            "Default": {
                "PoolId": "us-west-1:6c43caeb-8d52-465d-9005-cdc21332d345",
                "Region": "us-west-1"
            }
        }
    },
    "CognitoUserPool": {
        "Default": {
            "PoolId": "us-west-1_6MwAIOy68",
            "AppClientId": "28qavv8ofqkd1vb076lbm5v2hd",
            "Region": "us-west-1"
        }
    },
    "Auth": {
        "Default": {
            "authenticationFlowType": "USER_SRP_AUTH",
            "socialProviders": [],
            "usernameAttributes": [
                "EMAIL"
            ],
            "signupAttributes": [],
            "passwordProtectionSettings": {
                "passwordPolicyMinLength": 8,
                "passwordPolicyCharacters": [
                    "REQUIRES_LOWERCASE",
                    "REQUIRES_NUMBERS",
                    "REQUIRES_SYMBOLS",
                    "REQUIRES_UPPERCASE"
                ]
            },
            "mfaConfiguration": "OFF",
            "mfaTypes": [
                "SMS"
            ],
            "verificationMechanisms": [
                "EMAIL"
            ]
        }
    },
    "S3TransferUtility": {
        "Default": {
            "Bucket": "screenlake-zip-dev115119-dev",
            "Region": "us-west-1"
        }
    }
}
    )JSON";


// Return the JSON string as a Java String
return env->NewStringUTF(jsonConfig);
}