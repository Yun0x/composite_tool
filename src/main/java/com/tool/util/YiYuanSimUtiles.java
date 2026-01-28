package com.tool.util;

import com.tool.vo.TSimcardInfo;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.*;

public class YiYuanSimUtiles {
    //APP_KEY和SECRET_KEY来自移远物联网卡管理网站http://iot.quectel.com/connect_api_management.html
    private static final String APP_KEY = "l20r58696d0H3t49";
    private static final String SECRET_KEY = "Rx314KOv";
    private static final String REQUEST_URL = "https://api.quectel.com/openapi/router";

    public static void main(String[] args) {
        JSONArray jsonArray = GetSIMCardsJsonArrayByIccids("8986032343202427333");
        System.out.println(jsonArray.toString());
    }

    //移远签名算法
    public static String createSign(SortedMap<Object, Object> parameters) {
        StringBuffer sb = new StringBuffer();
        Set es = parameters.entrySet();//所有参与传参的参数按照accsii排序（升序）
        Iterator it = es.iterator();
        sb.append(SECRET_KEY);
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String k = (String) entry.getKey();
            Object v = entry.getValue();
            if (null != v && !"".equals(v) && !"sign".equals(k)) {
                sb.append(k + v);
            }
        }
        sb.append(SECRET_KEY);
        byte[] bts = DigestUtils.sha1(sb.toString());
        String sign = MD5.byteArrayToHexString(bts);
        return sign;
    }

    public static String getRequestPara(SortedMap<Object, Object> parameters) {
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<Object, Object> entry : parameters.entrySet()) {
            sb.append(entry.getKey() + "=" + entry.getValue());
            sb.append("&");
        }
        return sb.substring(0, sb.length() - 1);
    }

    public static List<TSimcardInfo> GetSIMCardListByIccids(String iccids) {
        List<TSimcardInfo> tSimcardInfoList = new ArrayList<TSimcardInfo>();
        JSONArray jsonArray = GetSIMCardsJsonArrayByIccids(iccids);
        for (Object object1 : jsonArray) {
            JSONObject obj = (JSONObject) object1;
            TSimcardInfo simcardInfo = new TSimcardInfo();
            simcardInfo.setMsisdn(obj.getString("msisdn"));//msisdn
            simcardInfo.setIccid(obj.getString("iccid"));//iccid
            simcardInfo.setImsi(obj.getString("imsi"));
            simcardInfo.setCarrier(obj.getString("supplier"));//供应商
            simcardInfo.setDataPlan(obj.getString("setMeal"));//套餐
            String active = obj.getString("active");//激活状态
            if (active.equals("激活")) simcardInfo.setActive("1");
            else if (active.equals("未激活")) simcardInfo.setActive("0");
            else simcardInfo.setActive(active);
            if (EmptyUtils.isNotEmpty(obj.getString("activateTime")))//激活时间
                simcardInfo.setActiveDate(DateUtils.doFormatDate(obj.getString("activateTime"), "yyyy-MM-dd"));
            if (EmptyUtils.isNotEmpty(obj.getString("expiryDate")))//到期时间
                simcardInfo.setExpiryDate(new Date(DateUtils.doFormatDate(obj.getString("expiryDate"), "yyyy-MM-dd").getTime() - 864000000));
            String sta = obj.getString("status");//卡状态
            if (sta.equals("未知")) simcardInfo.setAccountStatus("0");
            else if (sta.equals("测试期正常")) simcardInfo.setAccountStatus("1");
            else if (sta.equals("待激活")) simcardInfo.setAccountStatus("2");
            else if (sta.equals("正常")) simcardInfo.setAccountStatus("3");
            else if (sta.equals("停机")) simcardInfo.setAccountStatus("4");
            else if (sta.equals("预销号")) simcardInfo.setAccountStatus("6");
            else if (sta.equals("销号")) simcardInfo.setAccountStatus("7");
            else simcardInfo.setAccountStatus(sta);
            simcardInfo.setDataUsage(obj.getString("flow"));//当月已用流量
            simcardInfo.setFlag1("移远卡");//
            simcardInfo.setFlag2("2");//2:移远卡
            tSimcardInfoList.add(simcardInfo);
        }
        return tSimcardInfoList;
    }

    public static List<TSimcardInfo> GetSIMCardListByMsisdns(String msisdns) {
        List<TSimcardInfo> tSimcardInfoList = new ArrayList<TSimcardInfo>();
        JSONArray jsonArray = GetSIMCardsJsonArrayByMsisdns(msisdns);
        for (Object object1 : jsonArray) {
            JSONObject obj = (JSONObject) object1;
            TSimcardInfo simcardInfo = new TSimcardInfo();
            simcardInfo.setMsisdn(obj.getString("msisdn"));//msisdn
            simcardInfo.setIccid(obj.getString("iccid"));//iccid
            simcardInfo.setImsi(obj.getString("imsi"));
            simcardInfo.setCarrier(obj.getString("supplier"));//供应商
            simcardInfo.setDataPlan(obj.getString("setMeal"));//套餐
            String active = obj.getString("active");//激活状态
            if (active.equals("激活")) simcardInfo.setActive("1");
            else if (active.equals("未激活")) simcardInfo.setActive("0");
            else simcardInfo.setActive(active);
            if (EmptyUtils.isNotEmpty(obj.getString("activateTime")))//激活时间
                simcardInfo.setActiveDate(DateUtils.doFormatDate(obj.getString("activateTime"), "yyyy-MM-dd"));
            if (EmptyUtils.isNotEmpty(obj.getString("expiryDate")))//到期时间
                simcardInfo.setExpiryDate(new Date(DateUtils.doFormatDate(obj.getString("expiryDate"), "yyyy-MM-dd").getTime() - 864000000));
            String sta = obj.getString("status");//卡状态
            if (sta.equals("未知")) simcardInfo.setAccountStatus("0");
            else if (sta.equals("测试期正常")) simcardInfo.setAccountStatus("1");
            else if (sta.equals("待激活")) simcardInfo.setAccountStatus("2");
            else if (sta.equals("正常")) simcardInfo.setAccountStatus("3");
            else if (sta.equals("停机")) simcardInfo.setAccountStatus("4");
            else if (sta.equals("预销号")) simcardInfo.setAccountStatus("6");
            else if (sta.equals("销号")) simcardInfo.setAccountStatus("7");
            else simcardInfo.setAccountStatus(sta);
            simcardInfo.setDataUsage(obj.getString("flow"));//当月已用流量
            simcardInfo.setFlag1("移远卡");//
            simcardInfo.setFlag2("2");//2:移远卡
            tSimcardInfoList.add(simcardInfo);
        }
        return tSimcardInfoList;
    }


    public static JSONArray GetSIMCardsJsonArrayByIccids(String iccids) {

        JSONArray simCardInfoArray = new JSONArray();
        HttpClient httpclient = HttpClientBuilder.create().build();
        HttpResponse response;
        HttpPost httppost = new HttpPost(REQUEST_URL);
        try {
            SortedMap<Object, Object> parameters = new TreeMap<Object, Object>();
            //请求参数整理，生成签名
            parameters.put("appKey", APP_KEY);
            parameters.put("t", String.valueOf(new Date().getTime() / 1000));
            parameters.put("method", "fc.function.cards.info");
            parameters.put("iccids", iccids);
            parameters.put("sign", createSign(parameters));
            //请求参数转换为JSONObject，便于请求
            StringEntity stringEntity = new StringEntity(getRequestPara(parameters));
            stringEntity.setContentType("application/x-www-form-urlencoded");
            httppost.setEntity(stringEntity);
            response = httpclient.execute(httppost);
            //检验状态码，如果成功接收数据
            int code = response.getStatusLine().getStatusCode();
            //System.out.println(code+"code");
            if (code == 200) {
                String rev = EntityUtils.toString(response.getEntity());//返回json格式： {"id": "","name": ""}
                JSONObject obj1 = JSONObject.fromObject(rev);
                simCardInfoArray = obj1.getJSONArray("list");
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

    public static JSONArray GetSIMCardsJsonArrayByMsisdns(String msisdns) {

        JSONArray simCardInfoArray =new JSONArray();
        HttpClient httpclient = HttpClientBuilder.create().build();
        HttpResponse response;
        HttpPost httppost = new HttpPost(REQUEST_URL);
        try {
            SortedMap<Object,Object> parameters = new TreeMap<Object,Object>();
            //请求参数整理，生成签名
            parameters.put("appKey", APP_KEY);
            parameters.put("t", String.valueOf(new Date().getTime()/1000));
            parameters.put("method", "fc.function.cards.info");
            parameters.put("msisdns", msisdns);
            parameters.put("sign", createSign(parameters));
            //请求参数转换为JSONObject，便于请求
            StringEntity stringEntity = new StringEntity(getRequestPara(parameters));
            stringEntity.setContentType("application/x-www-form-urlencoded");
            httppost.setEntity(stringEntity);
            response = httpclient.execute(httppost);
            //检验状态码，如果成功接收数据
            int code = response.getStatusLine().getStatusCode();
            //System.out.println(code+"code");
            if (code == 200) {
                String rev = EntityUtils.toString(response.getEntity());//返回json格式： {"id": "","name": ""}
                JSONObject obj1 = JSONObject.fromObject(rev);
                simCardInfoArray = obj1.getJSONArray("list");
                return simCardInfoArray;
            }
        }
        catch (ClientProtocolException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                ((CloseableHttpClient) httpclient).close();
            }catch (Exception e) {
                e.printStackTrace();
            }
        }

        return simCardInfoArray;
    }

}
