/*
 *  Copyright (c) 2023, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.carbon.connector.azure.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobStorageException;
import com.google.gson.Gson;
import com.microsoft.aad.msal4j.MsalException;
import org.apache.axiom.om.OMElement;
import org.apache.commons.lang.StringUtils;
import org.apache.synapse.MessageContext;
import org.wso2.carbon.connector.azure.storage.connection.AzureStorageConnectionHandler;
import org.wso2.carbon.connector.azure.storage.exceptions.InvalidConfigurationException;
import org.wso2.carbon.connector.azure.storage.util.AzureConstants;
import org.wso2.carbon.connector.azure.storage.util.AzureUtil;
import org.wso2.carbon.connector.azure.storage.util.Error;
import org.wso2.carbon.connector.azure.storage.util.ResultPayloadCreator;
import org.wso2.carbon.connector.core.AbstractConnector;
import org.wso2.carbon.connector.core.ConnectException;
import org.wso2.carbon.connector.core.connection.ConnectionHandler;

import java.util.HashMap;
import java.util.Map;
import javax.xml.stream.XMLStreamException;

/**
 * This class for performing upload metadata operation.
 */
public class MetadataUploader extends AbstractConnector {

    public void connect(MessageContext messageContext) {

        Object containerName = messageContext.getProperty(AzureConstants.CONTAINER_NAME);
        Object fileName = messageContext.getProperty(AzureConstants.FILE_NAME);
        Object metadata = messageContext.getProperty(AzureConstants.METADATA);

        if (containerName == null || fileName == null || metadata == null) {
            AzureUtil.setErrorPropertiesToMessage(messageContext, Error.MISSING_PARAMETERS, "Mandatory " +
                    "parameters [containerName], [fileName] and [metadata] cannot be empty.");
            handleException("Mandatory parameters [containerName], [fileName] and [metadata] cannot be empty.",
                    messageContext);
        }
        ConnectionHandler handler = ConnectionHandler.getConnectionHandler();
        String status = AzureConstants.ERR_UNKNOWN_ERROR_OCCURRED;
        try {
            String connectionName = AzureUtil.getConnectionName(messageContext);
            AzureStorageConnectionHandler azureStorageConnectionHandler = (AzureStorageConnectionHandler)
                    handler.getConnection(AzureConstants.CONNECTOR_NAME, connectionName);
            BlobServiceClient blobServiceClient = azureStorageConnectionHandler.getBlobServiceClient();
            BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName.toString());
            if (containerClient.exists()) {
                BlobClient blobClient = containerClient.getBlobClient(fileName.toString());
                if (blobClient.exists()) {
                    Gson gson = new Gson();
                    HashMap<String, String> metadataMap = new HashMap<>();
                    Map<String, String> map = gson.fromJson((String) metadata, Map.class);
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        if (StringUtils.isNotEmpty(entry.getValue())) {
                            metadataMap.put(entry.getKey(), entry.getValue());
                        }
                    }
                    blobClient.setMetadata(metadataMap);
                    status = AzureConstants.STATUS_SUCCESS;
                } else {
                    status = AzureConstants.ERR_BLOB_DOES_NOT_EXIST;
                }
            } else {
                status = AzureConstants.ERR_CONTAINER_DOES_NOT_EXIST;
            }
        } catch (InvalidConfigurationException e) {
            AzureUtil.setErrorPropertiesToMessage(messageContext, Error.INVALID_CONFIGURATION, e.getMessage());
            handleException(AzureConstants.ERROR_LOG_PREFIX + e.getMessage(), messageContext);
        } catch (ConnectException e) {
            AzureUtil.setErrorPropertiesToMessage(messageContext, Error.CONNECTION_ERROR, e.getMessage());
            handleException(AzureConstants.ERROR_LOG_PREFIX + e.getMessage(), messageContext);
        } catch (MsalException e) {
            AzureUtil.setErrorPropertiesToMessage(messageContext, Error.AUTHENTICATION_ERROR, e.getMessage());
            handleException(AzureConstants.ERROR_LOG_PREFIX + e.getMessage(), messageContext);
        } catch (BlobStorageException e) {
            AzureUtil.setErrorPropertiesToMessage(messageContext, Error.BLOB_STORAGE_ERROR, e.getMessage());
            handleException(AzureConstants.ERROR_LOG_PREFIX + e.getMessage(), messageContext);
        } catch (Exception e) {
            AzureUtil.setErrorPropertiesToMessage(messageContext, Error.GENERAL_ERROR, e.getMessage());
            handleException(AzureConstants.ERROR_LOG_PREFIX + e.getMessage(), messageContext);
        }
        generateResults(messageContext, status);
    }

    /**
     * Generate the result
     *
     * @param messageContext The message context that is processed by a handler in the handle method
     * @param status   Result of the status (true/false)
     */
    private void generateResults(MessageContext messageContext, String status) {
        boolean isSuccess = AzureConstants.STATUS_SUCCESS.equals(status);
        String response = AzureUtil.generateResultPayload(isSuccess, !isSuccess ? status : "");
        OMElement element = null;
        try {
            element = ResultPayloadCreator.performSearchMessages(response);
        } catch (XMLStreamException e) {
            handleException("Unable to build the message.", e, messageContext);
        }
        ResultPayloadCreator.preparePayload(messageContext, element);
    }
}
