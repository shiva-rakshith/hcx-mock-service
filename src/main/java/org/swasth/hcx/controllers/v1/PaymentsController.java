package org.swasth.hcx.controllers.v1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.swasth.hcx.controllers.BaseController;
import org.swasth.hcx.utils.Constants;

import java.util.Map;

@RestController()
@RequestMapping(value =  "/"+ "${hcx_application.api_version}" + "/paymentnotice")
public class PaymentsController extends BaseController {

    @Value("${kafka.topic.payment}")
    private String kafkaTopic;

    @RequestMapping(value = "/request", method = RequestMethod.POST)
    public ResponseEntity<Object> paymentNoticeRequest(@RequestBody Map<String, Object> requestBody) throws Exception {
        return validateReqAndPushToKafka(requestBody, Constants.PAYMENT_NOTICE_REQUEST,Constants.PAYMENT_NOTICE_ONREQUEST, kafkaTopic);
    }

    @RequestMapping(value = "/on_request", method = RequestMethod.POST)
    public ResponseEntity<Object> paymentNoticeOnRequest(@RequestBody Map<String, Object> requestBody) throws Exception {
        return validateReqAndPushToKafka(requestBody, Constants.PAYMENT_NOTICE_ONREQUEST,Constants.PAYMENT_NOTICE_ONREQUEST, kafkaTopic);
    }

}
