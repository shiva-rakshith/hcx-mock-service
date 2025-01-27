package org.swasth.hcx.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.swasth.jose.jwe.JweRequest;
import org.swasth.jose.jwe.key.PublicKeyLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.swasth.hcx.utils.Constants.PARTIAL_STATUS;


@Component
public class OnActionCall {

    @Autowired
    Environment env;

    private String onCheckPayloadType;

    public static String getRandomChestItem(List<String> items) {
        return items.get(new Random().nextInt(items.size()));
    }

    public InputStream getFileAsIOStream(final String fileName)
    {
        InputStream ioStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream(fileName);

        if (ioStream == null) {
            throw new IllegalArgumentException(fileName + " is not found");
        }
        return ioStream;
    }

    public Map<String, String> encryptPayload(String filePath, Map<String, Object> headers, Map<String, Object> payload) throws Exception{
        Map<String, String> encryptedObject;
        //File file = new File(filePath);
        InputStream io = getFileAsIOStream(filePath);
        Reader fileReader = new InputStreamReader(io);
        RSAPublicKey rsaPublicKey = PublicKeyLoader.loadPublicKeyFromX509Certificate(fileReader);
        JweRequest jweRequest = new JweRequest(headers, payload);
        jweRequest.encryptRequest(rsaPublicKey);
        encryptedObject = jweRequest.getEncryptedObject();
        return encryptedObject;

    }

    public Map<String, String> jwsEncryptPayload(String filePath, Map<String, Object> payload) throws Exception{
        RSAPrivateKey rsaPrivateKey;
        Map<String, String> encryptedObject = new HashMap<>();
        InputStream io = getFileAsIOStream(filePath);
        Reader fileReader = new InputStreamReader(io);
        PemReader pemReader = new PemReader(fileReader);
        PemObject pemObject = pemReader.readPemObject();
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(pemObject.getContent());
        KeyFactory factory = KeyFactory.getInstance("RSA");
        rsaPrivateKey = (RSAPrivateKey) factory.generatePrivate(privateKeySpec);
        String jwsEncrypted = Jwts.builder().setClaims(payload).signWith(SignatureAlgorithm.RS256, rsaPrivateKey).compact();
        encryptedObject.put("payload",jwsEncrypted);
        System.out.println("JWS Payload " + encryptedObject);
        return encryptedObject;
    }
    @Async("asyncExecutor")
    public void createOnActionHeaders(Map<String, Object> headers, Map<String, Object> map, String onApiAction, String publicKeyPath) throws Exception{

        //changing the onApiAction to match the version from application.yaml
        System.out.println("Commencing work");
        onApiAction = "/" + env.getProperty("hcx_application.api_version")+ "/" + onApiAction.split("/")[2] + "/" + onApiAction.split("/")[3];
        System.out.println("onApiAction" + onApiAction + " " + env.getProperty("hcx_application.token_url"));
                Map<String, Object> returnHeaders =  new HashMap<>();
        DateTime currentTime = DateTime.now();
        returnHeaders.putAll(headers);
        System.out.println("headers before" + headers);
        returnHeaders.put("x-hcx-sender_code",headers.get("x-hcx-recipient_code"));
        returnHeaders.put("x-hcx-recipient_code",headers.get("x-hcx-sender_code"));
        returnHeaders.put("x-hcx-api_call_id", UUID.randomUUID().toString());
        returnHeaders.put("x-hcx-timestamp",currentTime.toString());
        returnHeaders.put("x-hcx-status", Constants.COMPLETE_STATUS);
        onCheckPayloadType = "jweResponse";
        if(headers.containsKey("x-hcx-delay") == true){
            Thread.sleep(Long.parseLong(String.valueOf(headers.get("x-hcx-delay"))));
        }
        if(headers.containsKey("x-hcx-test_random") == true){
            returnHeaders.remove("x-hcx-test_random");
            returnHeaders.put("x-hcx-status", getRandomChestItem(Constants.STATUS_RESPONSES));
            if(returnHeaders.get("x-hcx-status") == Constants.COMPLETE_STATUS || returnHeaders.get("x-hcx-status") == PARTIAL_STATUS) {
                onCheckPayloadType = "jweResponse";
            }else if(returnHeaders.get("x-hcx-status") == Constants.ERROR_STATUS){
                Map<String,String> error = new HashMap<>();
                error.put("code",getRandomChestItem(Constants.RECIPIENT_ERROR_CODES));
                error.put("message","Error encountered");
                error.put("trace",null);
                returnHeaders.put("x-hcx-error_details",error);
                returnHeaders.put("x-hcx-status", Constants.ERROR_STATUS);
                onCheckPayloadType = "protocolResponse";
            }else if(returnHeaders.get("x-hcx-status") == Constants.REDIRECT_STATUS){
                returnHeaders.put("x-hcx-redirect_to",UUID.randomUUID().toString());
                onCheckPayloadType = "protocolResponse";
            }
        }else {
            if (headers.containsKey("x-hcx-debug_flag_test")) {
                returnHeaders.put("x-hcx-debug_flag", headers.get("x-hcx-debug_flag_test"));
                returnHeaders.remove("x-hcx-debug_flag_test");
                onCheckPayloadType = "protocolResponse";
            }
            if (headers.containsKey("x-hcx-status_test")) {
                returnHeaders.put("x-hcx-status", headers.get("x-hcx-status_test"));
                returnHeaders.remove("x-hcx-status_test");
                onCheckPayloadType = "protocolResponse";
            }
            if (headers.containsKey("x-hcx-error_details_test")) {
                returnHeaders.put("x-hcx-error_details", headers.get("x-hcx-error_details_test"));
                returnHeaders.remove("x-hcx-error_details_test");
                returnHeaders.put("x-hcx-status", Constants.ERROR_STATUS);
                onCheckPayloadType = "protocolResponse";
            }
            if (headers.containsKey("x-hcx-debug_details_test")) {
                returnHeaders.put("x-hcx-debug_details", headers.get("x-hcx-debug_details_test"));
                returnHeaders.remove("x-hcx-debug_details_test");
                onCheckPayloadType = "protocolResponse";
            }
        }
        System.out.println("on check map"+returnHeaders);
        //creating an on check payload
        System.out.println("onCheckPayloadType"+ onCheckPayloadType);
        if (onCheckPayloadType == "jweResponse") {
            Map<String, String> encryptedOnPayload = encryptPayload(publicKeyPath, returnHeaders, map);
            sendOnAction(onApiAction,encryptedOnPayload);
            System.out.println("on check payload    " + encryptedOnPayload);
            System.out.println("return ciphertext unencrypted " + map);
        }else{
            sendOnAction(onApiAction,returnHeaders);
            System.out.println("on check payload    " + returnHeaders);
        }
    }

    public void sendOnAction(String onApiCall, Map<String, ?> encryptedPayload) throws Exception{
        System.out.println("Timestamp before on_action call: "+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()));
        HttpResponse<String> response = Unirest.post(env.getProperty("hcx_application.token_url"))
                .header("content-type", "application/x-www-form-urlencoded")
                .field("client_id", "registry-frontend")
                .field("username", env.getProperty("hcx_application.user"))
                .field("password", env.getProperty("hcx_application.password"))
                .field("grant_type", "password")
                .asString();
        ObjectMapper mapper = new ObjectMapper();
        System.out.println("url "+env.getProperty("hcx_application.url"));
        System.out.println("version" + env.getProperty("hcx_application.api_version"));
        System.out.println("user"+  env.getProperty("hcx_application.user"));
        Map<String, String> responseBody = mapper.readValue(response.getBody(), Map.class);
        System.out.println("access token "+ responseBody.get("access_token"));
        HttpResponse<String> onActionResponse = Unirest.post(env.getProperty("hcx_application.url") + onApiCall)
                .header("Authorization", "Bearer " + responseBody.get("access_token").toString())
                .header("Content-Type", "application/json")
                .body(encryptedPayload)
                .asString();

        Map<String, String> res = mapper.readValue(onActionResponse.getBody(), Map.class);
        System.out.println("response "+ res);
    }

    public String searchRegistry(String email) throws Exception {
        System.out.println("Timestamp before registry call: "+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()));
        HttpResponse<String> response = Unirest.post(env.getProperty("hcx_application.token_url"))
                .header("content-type", "application/x-www-form-urlencoded")
                .field("client_id", "registry-frontend")
                .field("username", env.getProperty("hcx_application.user"))
                .field("password", env.getProperty("hcx_application.password"))
                .field("grant_type", "password")
                .asString();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> responseBody = mapper.readValue(response.getBody(), Map.class);
        System.out.println("access token "+ responseBody.get("access_token"));

        //creating filter for search query on email
        HashMap<String, HashMap<String, Object>> filter = new HashMap<>();
        filter.put("filters",new HashMap<String, Object>(Map.of("primary_email", new HashMap<>(Map.of("eq", email)))));
        System.out.println("filters " + filter);
        HttpResponse<String> onActionResponse = Unirest.post(env.getProperty("hcx_application.registry_url"))
                .header("Authorization", "Bearer " + responseBody.get("access_token").toString())
                .header("Content-Type", "application/json")
                .body(filter)
                .asString();
        Map<String, ArrayList> resArray = mapper.readValue(onActionResponse.getBody(), Map.class);
        ArrayList participant =resArray.get("participants");
        Map<String, Object> res = (Map<String, Object>) participant.get(0);
        System.out.println("res for filter " + res.get("participant_code"));
        return (String) res.get("osid");
    }
}
