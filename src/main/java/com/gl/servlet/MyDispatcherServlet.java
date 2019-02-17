package com.gl.servlet;

import com.gl.annotion.MyAutoGl;
import com.gl.annotion.MyControl;
import com.gl.annotion.MyRequestMapping;
import com.gl.annotion.MyService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

public class MyDispatcherServlet extends HttpServlet  {

    private Logger log= Logger.getLogger("init");

    private Properties properties = new Properties();

    private List<String> classNames = new ArrayList<>();

    private Map<String, Object> ioc = new HashMap<>();
    //handlerMapping的类型可以自定义为Handler
    private Map<String, Method> handlerMapping = new  HashMap<>();

    private Map<String, Object> controllerMap  =new HashMap<>();


    @Override
    public void init (ServletConfig config) throws ServletException{
        super.init();
        //1.加载配置文件，填充properties字段；
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2.根据properties，初始化所有相关联的类,扫描用户设定的包下面所有的类
        doScanner(properties.getProperty("scanPackage"));

        //3.拿到扫描到的类,通过反射机制,实例化,并且放到ioc容器中(k-v  beanName-bean) beanName默认是首字母小写
        doInstance();

        // 4.自动化注入依赖
        doAutowiredIoc();

        //5.初始化HandlerMapping(将url和method对应上)
        initHandlerMapping();

        doAutowiredControl();

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 注释掉父类实现，不然会报错：405 HTTP method GET is not supported by this URL
        //super.doPost(req, resp);
        log.info("执行MyDispatcherServlet的doPost()");
        try {
            //处理请求
            doDispatch(req,resp);
        } catch (Exception e) {
            resp.getWriter().write("500!! Server Exception");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.info("执行MyDispatcherServlet的doGet()");
        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            resp.getWriter().write("500!! Server Exception");
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if(handlerMapping.isEmpty()){
            return;
        }
        String url =req.getRequestURI();
        String contextPath = req.getContextPath();
        url=url.replace(contextPath, "").replaceAll("/+", "/");
        if(url.lastIndexOf('/')!=0){
            url=url.substring(1);
        }
        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 NOT FOUND!");
            log.info("404 NOT FOUND!");
            return;
        }
        Method method =this.handlerMapping.get(url);
        Class<?>[] parameterTypes = method.getParameterTypes();

        Map<String, String[]> parameterMap = req.getParameterMap();
        Object [] paramValues= new Object[parameterTypes.length];
        for (int i = 0; i<parameterTypes.length; i++){
            String requestParam = parameterTypes[i].getSimpleName();
            if (requestParam.equals("HttpServletRequest")){
                paramValues[i]=req;
                continue;
            }
            if (requestParam.equals("HttpServletResponse")){
                paramValues[i]=resp;
                continue;
            }
            if(requestParam.equals("String")){
                for (Map.Entry<String, String[]> param : parameterMap.entrySet()) {
                    String value =Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
                    paramValues[i]=value;
                }
            }
        }
        try {
            method.invoke(this.controllerMap.get(url), paramValues);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void  doLoadConfig(String location){
        InputStream resourceAsStream=this.getClass().getClassLoader().getResourceAsStream(location);
        log.info(location);

        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (null!=resourceAsStream){
                try {
                    resourceAsStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void doScanner(String packageName){
        URL url=this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.","/"));
        File dir =new File(url.getFile());
        for (File file:dir.listFiles()){
            if (file.isDirectory()){
                doScanner(packageName+"."+file.getName());
            }else{
                String className =packageName +"." +file.getName().replace(".class", "");
                classNames.add(className);
            }
        }

    }

    public void  doInstance(){
        if (classNames.isEmpty()){
            return;
        }

        for (String className:classNames){

            try {
                Class<?> clazz=Class.forName(className);
                if (clazz.isAnnotationPresent(MyControl.class)){
                    ioc.put(toLowerFirstWord(clazz.getSimpleName()),clazz.getDeclaredConstructor().newInstance());
                }else if (clazz.isAnnotationPresent(MyService.class)){
                    MyService myservice=clazz.getAnnotation(MyService.class);
                    String beanName=myservice.value();

                    if ("".equals(beanName.trim())){
                        beanName=toLowerFirstWord(clazz.getSimpleName());
                    }

                    Object instance=clazz.getDeclaredConstructor().newInstance();
                    ioc.put(beanName,instance);
                    Class [] interfaces=clazz.getInterfaces();

                    for (Class<?> i:interfaces){
                        ioc.put(i.getName(),instance);
                    }
                }else{
                    continue;
                }

            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }

        }
    }

    private void doAutowiredIoc(){

        if (ioc.isEmpty()){
            return;
        }
        for (Map.Entry<String,Object> entry:ioc.entrySet()){
            Field[] fields=entry.getValue().getClass().getDeclaredFields();
            for (Field field:fields){
                if (!field.isAnnotationPresent(MyAutoGl.class)){
                    continue;
                }
                MyAutoGl autowired= field.getAnnotation(MyAutoGl.class);
                String beanName=autowired.value().trim();
                if ("".equals(beanName)){
                    beanName=field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                }catch (Exception e){
                    e.printStackTrace();
                    continue;
                }

            }
        }
    }

    private void doAutowiredControl(){

        if (controllerMap.isEmpty()){
            return;
        }
        for (Map.Entry<String,Object> entry:controllerMap.entrySet()){
            //包括私有的方法，在spring中没有隐私，@MyAutowired可以注入public、private字段
            Field[] fields=entry.getValue().getClass().getDeclaredFields();
            for (Field field:fields){
                if (!field.isAnnotationPresent(MyAutoGl.class)){
                    continue;
                }
                MyAutoGl autowired= field.getAnnotation(MyAutoGl.class);
                String beanName=autowired.value().trim();
                if ("".equals(beanName)){
                    beanName=field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                }catch (Exception e){
                    e.printStackTrace();
                    continue;
                }

            }
        }
    }

    private void initHandlerMapping(){

        if(ioc.isEmpty()){
            return;
        }
        try {
            for (Map.Entry<String, Object> entry: ioc.entrySet()) {
                Class<? extends Object> clazz = entry.getValue().getClass();
                if(!clazz.isAnnotationPresent(MyControl.class)){
                    continue;
                }

                //拼url时,是controller头的url拼上方法上的url
                String baseUrl ="";
                if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                    MyRequestMapping annotation = clazz.getAnnotation(MyRequestMapping.class);
                    baseUrl=annotation.value();
                }
                Method[] methods = clazz.getMethods();
                for (Method method : methods) {
                    if(!method.isAnnotationPresent(MyRequestMapping.class)){
                        continue;
                    }
                    MyRequestMapping annotation = method.getAnnotation(MyRequestMapping.class);
                    String url = annotation.value();

                    url =(baseUrl+"/"+url).replaceAll("/+", "/");
                    handlerMapping.put(url,method);
                    controllerMap.put(url,clazz.newInstance());
                    System.out.println(url+","+method);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private String toLowerFirstWord(String name){

        char[] charArray = name.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }
}
