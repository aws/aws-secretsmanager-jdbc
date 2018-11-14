/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

/**
 * This package contains JDBC drivers that use secret IDs rather than hard-coded database credentials. They accomplish
 * this by substituting a secret ID specified by the user field in their <code>connect</code> properties with the
 * associated credentials from Secrets Manager. The call to <code>connect</code> is then propagated to a real JDBC
 * driver that actually establishes the connection. See the <code>AWSSecretsManagerDriver</code> class and the
 * individual drivers for configuration and usage details.
 */
package com.amazonaws.secretsmanager.sql;
