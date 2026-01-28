package com.tool.util;

import com.tool.vo.TSimcardInfo;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

public class HeZhouSimUtiles {
    //APP_KEY和SECRET_KEY来自合宙物联网卡管理网站http://sim.openluat.com/apiinterface
    private static final String APP_KEY = "yhgunq0xul5nt82l";
    private static final String SECRET_KEY = "asGy21Zs4h3YpZQbIlMAZclF6a8Opc8qa3qW0DqC9QGWcR7Ju6YVuMgV8q2bxCnl";


    /**
     * 构造Basic Auth认证token
     *
     * @return
     */
    private static String getToken() {
        String auth = APP_KEY + ":" + SECRET_KEY;
        String authHeader = "Basic " + Base64Utils.encode(auth.getBytes());
        return authHeader;
    }

    public static void main(String args[]) {

        //GetExpiryDateByIMSI("460042325803944");
        //GetSIMCardList();
        //GetAcountBalance();
        //GetProductInfoByIMSI("460041052603907");//老卡
        //GetProductInfoByIMSI("460042326205551");//按月充过值
        //GetProductInfoByIMSI("460042322816677");//新卡
        GetSimcardInfoByIMSI("460042322816677");//新卡
//        int loopNum=5;
//        while (loopNum!=0){
//            loopNum--;
//            int ret = ChargeSimCard("460042327106191","test");//测试卡
//            System.out.println(new Date().getTime());
//            if(ret!=-3){
//                System.out.print(ret);
//                break;
//            }
//        }
    }


    //从合宙卡系统中通过IMSI获取单张卡信息
    public static TSimcardInfo GetSimcardInfoByIMSI(String imsi) {

        TSimcardInfo simcardInfo = new TSimcardInfo();
        HttpClient httpclient = HttpClientBuilder.create().build();
        HttpResponse response;
        String uri = "http://api.openluat.com/sim/iotcard/card";
        HttpPost httppost = new HttpPost(uri);
        //添加http头信息
        httppost.addHeader("Authorization", getToken()); //认证token
        httppost.addHeader("Content-Type", "application/json");
        JSONObject obj = new JSONObject();
        obj.put("imsi", imsi);

        try {
            httppost.setEntity(new StringEntity(obj.toString()));
            response = httpclient.execute(httppost);
            //检验状态码，如果成功接收数据
            int code = response.getStatusLine().getStatusCode();
            //System.out.println(code+"code");
            if (code == 200) {
                String rev = EntityUtils.toString(response.getEntity());//返回json格式： {"id": "","name": ""}
                obj = JSONObject.fromObject(rev);
                Object ret = obj.get("data");
                simcardInfo.setImsi(imsi);
                simcardInfo.setMsisdn(((JSONObject) ret).getString("msisdn"));
                simcardInfo.setIccid(((JSONObject) ret).getString("iccid"));
                simcardInfo.setSpCode(((JSONObject) ret).getString("sp_code"));
                simcardInfo.setCarrier(((JSONObject) ret).getString("carrier"));
                simcardInfo.setDataPlan(((JSONObject) ret).getString("data_plan"));
                simcardInfo.setDataUsage(((JSONObject) ret).getString("data_usage"));
                simcardInfo.setAccountStatus(((JSONObject) ret).getString("account_status"));
                simcardInfo.setActive(((JSONObject) ret).getString("active"));
                simcardInfo.setTestUsedDataUsage(((JSONObject) ret).getString("test_used_data_usage"));
                simcardInfo.setDataBalance(((JSONObject) ret).getString("data_balance"));
                simcardInfo.setSupportSms(((JSONObject) ret).getString("support_sms"));
                simcardInfo.setExpiryDate(new Date((((JSONObject) ret).getLong("expiry_date") - 864000) * 1000));//实际到期时间减去10天
                simcardInfo.setTestValidDate(new Date(((JSONObject) ret).getLong("test_valid_date") * 1000));
                simcardInfo.setSilentValidDate(new Date(((JSONObject) ret).getLong("silent_valid_date") * 1000));
                simcardInfo.setActiveDate(new Date(((JSONObject) ret).getLong("active_date") * 1000));
                simcardInfo.setOutboundDate(new Date(((JSONObject) ret).getLong("outbound_date") * 1000));

                return simcardInfo;
            } else {
                return null;
            }

        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                ((CloseableHttpClient) httpclient).close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    //从合宙卡系统中通过多张IMSI获取多张卡信息
    public static JSONArray GetSimcardInfoByIccids(String iccids) {
        JSONArray simCardInfoArray = new JSONArray();
        HttpClient httpclient = HttpClientBuilder.create().build();
        HttpResponse response;
        String uri = "https://api.openluat.com/sim/iotcard/cards";
        HttpPost httppost = new HttpPost(uri);
        //添加http头信息
        httppost.addHeader("Authorization", getToken()); //认证token
        httppost.addHeader("Content-Type", "application/json");
        JSONObject obj = new JSONObject();
        obj.put("iccids", iccids);
        try {
            httppost.setEntity(new StringEntity(obj.toString()));
            response = httpclient.execute(httppost);
            //检验状态码，如果成功接收数据
            int code = response.getStatusLine().getStatusCode();
            if (code == 200) {
                String rev = EntityUtils.toString(response.getEntity());//返回json格式： {"id": "","name": ""}
                JSONObject obj1 = JSONObject.fromObject(rev);
                JSONObject data = obj1.getJSONObject("data");
                simCardInfoArray = data.getJSONArray("card_list");
                return simCardInfoArray;
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                ((CloseableHttpClient) httpclient).close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return simCardInfoArray;
    }


    public static List<TSimcardInfo> GetSIMCardListOnGroup(String iccidStr) {
        //获取最新的全部的合宙卡信息
        JSONArray simCardInfoArray = GetSimcardInfoByIccids(iccidStr);
        List<TSimcardInfo> tSimcardInfoList = new ArrayList<TSimcardInfo>();
        for (Object object : simCardInfoArray) {
            JSONObject jsonObject = (JSONObject) object;
            TSimcardInfo simcardInfo = new TSimcardInfo();
            simcardInfo.setImsi(jsonObject.getString("imsi"));
            simcardInfo.setMsisdn(jsonObject.getString("msisdn"));
            simcardInfo.setIccid(jsonObject.getString("iccid"));
            simcardInfo.setSpCode(jsonObject.getString("sp_code"));
            simcardInfo.setCarrier(jsonObject.getString("carrier"));
            simcardInfo.setDataPlan(jsonObject.getString("data_plan"));
            simcardInfo.setDataUsage(jsonObject.getString("data_usage"));
            simcardInfo.setAccountStatus(jsonObject.getString("account_status"));
            simcardInfo.setActive(jsonObject.getString("active"));
            simcardInfo.setTestUsedDataUsage(jsonObject.getString("test_used_data_usage"));
            simcardInfo.setDataBalance(jsonObject.getString("data_balance"));
            simcardInfo.setSupportSms(jsonObject.getString("support_sms"));
            simcardInfo.setExpiryDate(new Date((jsonObject.getLong("expiry_date") - 864000) * 1000)); // 减10天
            simcardInfo.setTestValidDate(new Date(jsonObject.getLong("test_valid_date") * 1000));
            simcardInfo.setSilentValidDate(new Date(jsonObject.getLong("silent_valid_date") * 1000));
            simcardInfo.setActiveDate(new Date(jsonObject.getLong("active_date") * 1000));
            simcardInfo.setOutboundDate(new Date(jsonObject.getLong("outbound_date") * 1000));
            simcardInfo.setFlag1("合宙卡"); // 合宙卡
            simcardInfo.setFlag2("1"); // 合宙卡
            tSimcardInfoList.add(simcardInfo);
        }
        return tSimcardInfoList;
    }

}
