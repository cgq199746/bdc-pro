package com.admin.security;



import com.admin.constants.AdminConstants;
import com.admin.dao.primary.AdminResourceDao;
import com.admin.entity.primary.AdminResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.intercept.InterceptorStatusToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * spring security默认的url授权方式需要预先硬编码在配置中，这里改写默认方式
 */
public class UrlSecurityInterceptor extends FilterSecurityInterceptor {

    @Autowired
    private AdminResourceDao adminResourceDao;

    private AntPathMatcher pathMatcher=new AntPathMatcher();

    @Autowired
    @Override
    public void setAccessDecisionManager(AccessDecisionManager accessDecisionManager) {
        super.setAccessDecisionManager(accessDecisionManager);
    }

    @Autowired
    @Override
    public void setAuthenticationManager(AuthenticationManager newManager) {
        super.setAuthenticationManager(newManager);
    }


    @Autowired
    @Override
    public void setSecurityMetadataSource(FilterInvocationSecurityMetadataSource newSource) {
        super.setSecurityMetadataSource(newSource);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        FilterInvocation fi = new FilterInvocation(request, response, chain);
        final String servletPath = ((HttpServletRequest) request).getServletPath();
        if (servletPath.equals("/login")) {
            fi.getChain().doFilter(fi.getRequest(), fi.getResponse());
            return;
        }

        if(servletPath.contains("/bootstrap/")
        || servletPath.contains("/dist/") || servletPath.contains("/plugins/")
                || servletPath.contains("/ueditor/")||
                servletPath.contains("/jquery-ztree/")|| servletPath.contains("/js/")){
            fi.getChain().doFilter(fi.getRequest(), fi.getResponse());
            return;
        }

        Authentication authentication= SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || authentication instanceof AnonymousAuthenticationToken){
            //没有认证的，直接就结束
            throw new AuthenticationCredentialsNotFoundException("您还没有登陆！！！");
        }

        String currentUser = authentication.getName();
        //不处理root账户的授权
        if (AdminConstants.ROOT_ACCOUNT.equalsIgnoreCase(currentUser)) {
            fi.getChain().doFilter(fi.getRequest(), fi.getResponse());
        }else{
            InterceptorStatusToken token = super.beforeInvocation(fi);
            //只保护在resource表中配置的资源
            if(token==null && isSecurityUrl(servletPath)){
                throw new AccessDeniedException("您无权访问");
            }
            try {
                fi.getChain().doFilter(fi.getRequest(), fi.getResponse());
            } finally {
                super.finallyInvocation(token);
            }
            super.afterInvocation(token, null);
        }
    }

    private boolean isSecurityUrl(String url){
        //List<AdminResource> resources=adminResourceDao.getEnableResources();
        List<AdminResource> resources=null;
        if(CollectionUtils.isEmpty(resources)){
            return false;
        }
        return !resources.stream().filter(item->pathMatcher.match(item.getUrl(),url))
                .collect(Collectors.toList()).isEmpty();

    }

    @Override
    public void destroy() {

    }
}
