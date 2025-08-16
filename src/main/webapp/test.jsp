<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>JSP测试页面</title>
</head>
<body>
    <h1>JSP测试页面</h1>
    <p>当前时间: <%= new java.util.Date() %></p>
    <p>服务器信息: <%= application.getServerInfo() %></p>
    <p>Servlet版本: <%= application.getMajorVersion() %>.<%= application.getMinorVersion() %></p>
    <p>JSP版本: <%= JspFactory.getDefaultFactory().getEngineInfo().getSpecificationVersion() %></p>
    
    <h2>请求信息</h2>
    <p>请求URI: <%= request.getRequestURI() %></p>
    <p>上下文路径: <%= request.getContextPath() %></p>
    <p>Servlet路径: <%= request.getServletPath() %></p>
    <p>查询字符串: <%= request.getQueryString() %></p>
</body>
</html> 