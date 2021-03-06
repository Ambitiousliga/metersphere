package io.metersphere.api.dto.automation.parse;

import com.alibaba.fastjson.JSONArray;
import io.metersphere.api.dto.ApiTestImportRequest;
import io.metersphere.api.dto.definition.parse.har.HarUtils;
import io.metersphere.api.dto.definition.parse.har.model.*;
import io.metersphere.api.dto.definition.request.MsScenario;
import io.metersphere.api.dto.definition.request.MsTestElement;
import io.metersphere.api.dto.definition.request.sampler.MsHTTPSamplerProxy;
import io.metersphere.api.dto.scenario.KeyValue;
import io.metersphere.api.jmeter.RequestResult;
import io.metersphere.api.jmeter.ResponseResult;
import io.metersphere.api.parse.HarScenarioAbstractParser;
import io.metersphere.api.service.ApiScenarioModuleService;
import io.metersphere.base.domain.ApiScenarioModule;
import io.metersphere.base.domain.ApiScenarioWithBLOBs;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.LogUtil;
import org.apache.commons.lang3.ObjectUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class HarScenarioParser extends HarScenarioAbstractParser<ScenarioImport> {

    @Override
    public ScenarioImport parse(InputStream source, ApiTestImportRequest request) {
        Har har = null;
        try {
            String sourceStr = getApiTestStr(source);
            har = HarUtils.read(sourceStr);
        } catch (Exception e) {
            MSException.throwException(e.getMessage());
            LogUtil.error(e.getMessage(), e);
        }

        if (ObjectUtils.isEmpty(har)) {
            MSException.throwException("解析失败，请确认选择的是 Har 格式！");
        }

        ScenarioImport scenarioImport = new ScenarioImport();

        String harName = request.getFileName();
        // 场景步骤
        LinkedList<MsTestElement> apiScenarioWithBLOBs = new LinkedList<>();

        MsScenario msScenario = new MsScenario();
        msScenario.setName(harName);
        this.projectId = request.getProjectId();
        if (!ObjectUtils.isEmpty(har.log)&&!ObjectUtils.isEmpty(har.log.entries)) {
            parseItem(har.log.entries, msScenario, apiScenarioWithBLOBs);
        }

        // 生成场景对象
        List<ApiScenarioWithBLOBs> scenarioWithBLOBs = new LinkedList<>();
        parseScenarioWithBLOBs(scenarioWithBLOBs, msScenario, request);
        scenarioImport.setData(scenarioWithBLOBs);
        return scenarioImport;
    }

    private void parseScenarioWithBLOBs(List<ApiScenarioWithBLOBs> scenarioWithBLOBsList, MsScenario msScenario, ApiTestImportRequest request) {
        ApiScenarioModule module = ApiScenarioImportUtil.getSelectModule(request.getModuleId());
        if (module == null) {
            ApiScenarioModuleService apiModuleService = CommonBeanFactory.getBean(ApiScenarioModuleService.class);
            module = apiModuleService.getNewModule(msScenario.getName(), projectId, 1);
        }

        ApiScenarioWithBLOBs scenarioWithBLOBs = parseScenario(msScenario);
        if (module != null) {
            scenarioWithBLOBs.setApiScenarioModuleId(module.getId());
            scenarioWithBLOBs.setModulePath("/" + module.getName());
        }
        scenarioWithBLOBsList.add(scenarioWithBLOBs);
    }

    private void parseItem(List<HarEntry> items, MsScenario scenario, LinkedList<MsTestElement> results) {
        for (HarEntry item : items) {
            MsHTTPSamplerProxy request = parseHar(item);
            if (request != null) {
                results.add(request);
            }
            request.setRequestResult(getRequestResult(request,item));
        }
        scenario.setHashTree(results);
    }

//    private List<ScenarioVariable> parseScenarioVariable(List<PostmanKeyValue> postmanKeyValues) {
//        if (postmanKeyValues == null) {
//            return null;
//        }
//        List<ScenarioVariable> keyValues = new ArrayList<>();
//        postmanKeyValues.forEach(item -> keyValues.add(new ScenarioVariable(item.getKey(), item.getValue(), item.getDescription(), VariableTypeConstants.CONSTANT.name())));
//        return keyValues;
//    }
private RequestResult getRequestResult(MsHTTPSamplerProxy samplerProxy,HarEntry harEntry) {
    HarRequest request = harEntry.request;
    HarResponse response = harEntry.response;

    RequestResult requestResult = new RequestResult();
    requestResult.setName("Response");
    requestResult.setUrl(request.url);
    requestResult.setMethod(request.method);
    if(samplerProxy.getBody()!= null){
        List<KeyValue> keyValueList = new ArrayList<>();
        if(!ObjectUtils.isEmpty(request.queryString)){
            for (HarQueryParm model : request.queryString) {
                KeyValue keyValue = new KeyValue(model.name,model.value);
                keyValueList.add(keyValue);
            }
        }
        if(!ObjectUtils.isEmpty(request.postData)&&!ObjectUtils.isEmpty(request.postData.params)){
            for (HarPostParam model : request.postData.params) {
                KeyValue keyValue = new KeyValue(model.name,model.value);
                keyValueList.add(keyValue);
            }
        }

        requestResult.setBody(JSONArray.toJSONString(keyValueList));
    }

    requestResult.setHeaders(JSONArray.toJSONString(request.headers));
    requestResult.setRequestSize(request.bodySize);
//    requestResult.setStartTime(result.getStartTime());
//    requestResult.setEndTime(result.getEndTime());
//    requestResult.setTotalAssertions(result.getAssertionResults().length);
//    requestResult.setSuccess(result.isSuccessful());
//    requestResult.setError(result.getErrorCount());
    if(!ObjectUtils.isEmpty(request.cookies)){
        requestResult.setCookies(JSONArray.toJSONString(request.cookies));
    }

    ResponseResult responseResult = requestResult.getResponseResult();
    responseResult.setHeaders(JSONArray.toJSONString(response.headers));
//    responseResult.setLatency(result.getLatency());
    responseResult.setResponseCode(String.valueOf(response.status));
    responseResult.setResponseSize(response.bodySize);
//    responseResult.setResponseTime(result.getTime());
    if(response.content != null && response.content.text != null){
        responseResult.setBody(response.content.text);
        responseResult.setResponseMessage(response.content.text);
    }

    return requestResult;
}
}
