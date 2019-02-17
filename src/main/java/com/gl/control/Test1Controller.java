package com.gl.control;

import com.gl.annotion.MyAutoGl;
import com.gl.annotion.MyControl;
import com.gl.annotion.MyRequestMapping;
import com.gl.annotion.MyRequestParam;
import com.gl.service.TestService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyControl()
@MyRequestMapping("test1")
public class Test1Controller {

    @MyAutoGl
    private TestService testService;

    @MyRequestMapping("test")
    public void myTest(HttpServletRequest request, HttpServletResponse response,
                       @MyRequestParam("param") String param){
        try {
            response.getWriter().write( "Test1Controller:the param you send is :"+param);
            testService.printParam(param);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
