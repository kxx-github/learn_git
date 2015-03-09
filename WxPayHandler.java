package com.mar114.servlet.wcp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.util.httpClient.HttpRequest;
import com.alipay.util.httpClient.HttpResultType;
import com.mar114.filter.MySSLProtocolSocketFactory;
import com.wcp.config.WcpConfig;
import com.wcp.util.MD5Util;
import com.wcp.util.XMLUtil;

public class WxPayHandler {
	private static final String CHARSET = "UTF-8";
	private static final String TENURL = "https://api.mch.weixin.qq.com/pay/unifiedorder";
	private SortedMap<String, String> packageParams = new TreeMap<String, String>();
	/** Token获取网关地址地址 */
	private String tokenUrl;
	/** 预支付网关url地址 */
	private String gateUrl;
	/** 查询支付通知网关URL */
	private String notifyUrl;
	/** 商户参数 */
	private String appid;
	private String appkey;
	private String partnerkey;
	private String appsecret;
	// 支付密钥123
	private String key = "e6738551519221186fc3fc66195cb088";
	// 商户号
	private String mchid;

	public WxPayHandler(String app_id, String app_secret, String app_key,
			String partner_key,String mchid) {
		this.appkey = app_key;
		this.key=app_key;
		this.appid = app_id;
		this.partnerkey = partner_key;
		this.appsecret = app_secret;
		this.mchid= mchid;
	}

	public void setParams(String key, String value) {
		packageParams.put(key, value);
	}

	public void init(String openid, String body, String outTradeNo,
			String totalFee,String notifyUrl) {
		setParams("openid", openid);
		setParams("body", body);
		setParams("out_trade_no", outTradeNo);
		setParams("trade_type", "JSAPI");
		setParams("notify_url", notifyUrl);
		setParams("total_fee", totalFee);

	}

	/**
	 * 作用：设置标配的请求参数，生成签名，生成接口参数xml
	 * @throws UnsupportedEncodingException 
	 */
	public String createXml() throws UnsupportedEncodingException {
		setParams("appid", this.appid);
		setParams("mch_id", this.mchid);
		setParams("nonce_str", getNonceStr());// 随机字符串
		setParams("sign", createSign());
		return arrayToXml();
		// $this->parameters["appid"] = WxPayConf_pub::APPID;//公众账号ID
		// $this->parameters["mch_id"] = WxPayConf_pub::MCHID;//商户号
		// $this->parameters["nonce_str"] = $this->createNoncestr();//随机字符串
		// $this->parameters["sign"] = $this->getSign($this->parameters);//签名
		// return $this->arrayToXml($this->parameters);
	}

	public String createSign() throws UnsupportedEncodingException {
		StringBuffer sb = new StringBuffer();
		Set es = packageParams.entrySet();
		Iterator it = es.iterator();
		while (it.hasNext()) {
			Map.Entry entry = (Map.Entry) it.next();
			String k = (String) entry.getKey();
			String v = (String) entry.getValue();
			if (null != v && !"".equals(v) && !"sign".equals(k)
					&& !"key".equals(k)) {
				sb.append(k + "=" + URLEncoder.encode(v,"utf-8") + "&");
			}
		}
		sb.append("key=" + this.key);
		System.out.println("md5 sb:" + sb);
		String sign = MD5Util.MD5Encode(sb.toString(), CHARSET).toUpperCase();
		System.out.println("sign="+sign);
		return sign;

	}
	public String getJsReqParameters(String prepayId) throws UnsupportedEncodingException{
		Map<String,Object> map  = new HashMap<String,Object>();
		map.put("appId", this.appid);
		map.put("timeStamp", Double.toString(System.currentTimeMillis()/1000));
		map.put("nonceStr", getNonceStr());
		map.put("package", "prepay_id="+prepayId);
		map.put("signType", "MD5");
		map.put("paySign", createSign());
		return JSON.toJSONString(map);
	}
	private String getNonceStr() {
		Random random = new Random();
		return MD5Util
				.MD5Encode(String.valueOf(random.nextInt(10000)), CHARSET);
	}

	public String getPrepayId() throws Exception {
		String xml = createXml();
		System.out.println(xml);
		String result = postXmlCurl(TENURL, xml);
		System.out.println(result);
		Map map = doXMLParse(result);
		if (map == null) {
			return null;
		}
		return map.get("prepay_id").toString();
	}
	private String executePOST(String xml) throws Exception {

		HttpRequest req = new HttpRequest(HttpResultType.STRING);

		req.setMethod(HttpRequest.METHOD_POST);
		HttpMethod getMethod = new PostMethod(TENURL);
		HttpClient httpClient = new HttpClient();
		Protocol myhttps = new Protocol("https", new MySSLProtocolSocketFactory(), 443);   
		Protocol.registerProtocol("https", myhttps);
		getMethod.setQueryString(xml);
		int statusCode = httpClient.executeMethod(getMethod);
		if(statusCode ==200){
			JSONObject j =JSON.parseObject(getMethod.getResponseBodyAsString(),JSONObject.class);
			String openId =j.getString("openid");
			if(openId!=null && !openId.trim().equals("")){
				System.out.println(openId);
				return openId;
			}else{
				System.out.println(j.toJSONString());
			}
		}
		getMethod.releaseConnection();
		return null;
	}
	private String postXmlCurl(String url, String xml) throws Exception {
//		StringEntity myEntity = new StringEntity("<html>你好啊啊</html>", "GBK"); 
//		httppost.addHeader("Content-Type", "text/xml"); 
//		httppost.setEntity(myEntity); 
//		HttpResponse response = httpclient.execute(httppost); 
//		HttpEntity resEntity = response.getEntity(); 
//
//		
		DefaultHttpClient client = new DefaultHttpClient();
		Protocol myhttps = new Protocol("https", new MySSLProtocolSocketFactory(), 443);   
		Protocol.registerProtocol("https", myhttps);
		HttpPost post = new HttpPost(url);
		// Construct a string entity
		  StringEntity entity = new StringEntity(xml);
		  // Set XML entity
		  post.setEntity(entity);
		  // Set content type of request header
		  post.setHeader("Content-Type", "text/xml;charset=utf-8");
		// Execute request and get the response
		  HttpResponse response = client.execute(post);
		  // Response Header - StatusLine - status code
		int  status = response.getStatusLine().getStatusCode();
//		HttpMethod getMethod = new PostMethod(TENURL);
//		HttpClient httpClient = new HttpClient();
//		Protocol myhttps = new Protocol("https", new MySSLProtocolSocketFactory(), 443);   
//		Protocol.registerProtocol("https", myhttps);
////		getMethod.addRequestHeader("Content-Type", "text/xml");
////		getMethod.setQueryString(xml);
//		int status = httpClient.executeMethod(getMethod);
		if (status == 200) {
			return IOUtils.toString(response.getEntity().getContent(),"utf-8");
		} else {
			throw new Exception(IOUtils.toString(response.getEntity().getContent()));
		}
	}

	/**
	 * 解析xml,返回第一级元素键值对。如果第一级元素有子节点，则此节点的值是子节点的xml数据。
	 * 
	 * @param strxml
	 * @return
	 * @throws JDOMException
	 * @throws IOException
	 */
	public static Map doXMLParse(String strxml) throws JDOMException,
			IOException {
		if (null == strxml || "".equals(strxml)) {
			return null;
		}

		Map m = new HashMap();
		InputStream in = new ByteArrayInputStream(strxml.getBytes());
		SAXBuilder builder = new SAXBuilder();
		Document doc = builder.build(in);
		Element root = doc.getRootElement();
		List list = root.getChildren();
		Iterator it = list.iterator();
		while (it.hasNext()) {
			Element e = (Element) it.next();
			String k = e.getName();
			String v = "";
			List children = e.getChildren();
			if (children.isEmpty()) {
				v = e.getTextNormalize();
			} else {
				v = XMLUtil.getChildrenText(children);
			}

			m.put(k, v);
		}
		// 关闭流
		in.close();

		return m;
	}

	private String arrayToXml() {
		StringBuilder sb = new StringBuilder("<xml>");
		Set es = packageParams.entrySet();
		Iterator it = es.iterator();
		while (it.hasNext()) {
			Map.Entry entry = (Map.Entry) it.next();
			String k = (String) entry.getKey();
			String v = (String) entry.getValue();
			if (null != v) {
				try {
					if (isNumeric(v)) {
						sb.append("<").append(k).append(">").append(URLEncoder.encode(v, "utf-8")).append("</")
								.append(k).append(">");
					} else {
						sb.append("<").append(k).append("><![CDATA[").append(URLEncoder.encode(v, "utf-8"))
								.append("]]></").append(k).append(">");
					}
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		sb.append("</xml>");
		return sb.toString();
	}

	public boolean isNumeric(String str) {
		try {
			Double d = Double.valueOf(str);
		} catch (java.lang.Exception e) {
			return false;
		}
		return true;
	}

	public static void main(String[] args) throws Exception {
		WxPayHandler handler = new WxPayHandler("1", "2", "3", "4",WcpConfig.partner);
		handler.init("openid", "cdadf", "23123123", "0.01","http://adfasdfdsf");
		String prepayId =handler.getPrepayId();
		String payPar = handler.getJsReqParameters(prepayId);
	}
}
