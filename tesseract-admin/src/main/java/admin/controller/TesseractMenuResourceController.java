package admin.controller;


import admin.entity.TesseractMenuResource;
import admin.entity.TesseractUser;
import admin.pojo.*;
import admin.security.SecurityUserContextHolder;
import admin.service.ITesseractMenuResourceService;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author nickle
 * @since 2019-07-10
 */
@RestController
@RequestMapping("/tesseract-menu")
public class TesseractMenuResourceController {

    @Autowired
    private ITesseractMenuResourceService tesseractMenuResourceService;

    /**
     * 菜单列表
     *
     * @param currentPage
     * @param pageSize
     * @param condition
     * @param startCreateTime
     * @param endCreateTime
     * @return: admin.pojo.CommonResponseVO
     * @author: LeoLee
     * @date: 2019/7/12
     */
    @PreAuthorize("hasPermission('menu', 'search') and hasRole('admin')")
    @RequestMapping("/menuList")
    public CommonResponseVO menuList(@NotNull @Min(1) Integer currentPage
            , @NotNull @Min(1) @Max(50) Integer pageSize, TesseractMenuResource condition,
                                     Long startCreateTime,
                                     Long endCreateTime) {
        IPage<TesseractMenuResource> userIPage = tesseractMenuResourceService.listByPage(currentPage, pageSize, condition, startCreateTime, endCreateTime);
        MenuVO menuVO = new MenuVO();
        PageVO pageVO = new PageVO();
        pageVO.setCurrentPage(userIPage.getCurrent());
        pageVO.setPageSize(userIPage.getSize());
        pageVO.setTotal(userIPage.getTotal());
        menuVO.setPageInfo(pageVO);
        menuVO.setMenuList(userIPage.getRecords());
        return CommonResponseVO.success(menuVO);
    }

    //@PreAuthorize("hasPermission(#condition, 'admin') and hasRole('admin')")
    @RequestMapping("/allMenu")
    public CommonResponseVO allMenu() {
        return CommonResponseVO.success(tesseractMenuResourceService.list());
    }


    @RequestMapping("/saveOrUpdateMenu")
    public CommonResponseVO saveOrUpdateMenu(@Validated @RequestBody TesseractMenuResource tesseractMenuResource) throws Exception {
        tesseractMenuResourceService.saveOrUpdateMenu(tesseractMenuResource);
        return CommonResponseVO.SUCCESS;
    }

    @RequestMapping("/deleteMenu")
    public CommonResponseVO deleteMenu(@NotNull Integer menuId) throws Exception {
        tesseractMenuResourceService.deleteMenu(menuId);
        return CommonResponseVO.SUCCESS;
    }
}
