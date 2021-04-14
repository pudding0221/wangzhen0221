package com.uzykj.mall.controller.fore;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.uzykj.mall.controller.BaseController;
import com.uzykj.mall.entity.*;
import com.uzykj.mall.service.*;
import com.uzykj.mall.util.PageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 前台天猫-产品详情页
 */
@Controller
public class ForeProductDetailsController extends BaseController {
    @Autowired
    private ProductService productService;
    @Autowired
    private UserService userService;
    @Autowired
    private ProductImageService productImageService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProductOrderItemService productOrderItemService;


    //转到前台天猫-产品详情页
    @RequestMapping(value = "product/{pid}", method = RequestMethod.GET)
    public String goToPage(HttpSession session, Map<String, Object> map,
                           @PathVariable("pid") String pid /*产品ID*/,
                           HttpServletResponse response) {
        logger.info("检查用户是否登录");
        Object userId = checkUser(session);

        if (userId != null) {
            logger.info("获取用户信息");
            User user = (User) session.getAttribute("user");
            //User user = userService.get(Integer.parseInt(userId.toString()));
            map.put("user", user);
        }/*else{
        	try {
        		response.sendRedirect(HostUtil.host + "AccountingOnline/user/checkLogin?url=bookstore/getsign");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }*/

        logger.info("获取产品ID");
        Integer product_id = Integer.parseInt(pid);
        logger.info("获取产品信息");
        Product product = productService.get(product_id);
        if (product == null || product.getProduct_isEnabled() == 1) {
            return "redirect:/404";
        }
        logger.info("获取产品子信息-分类信息");
        product.setProduct_category(categoryService.get(product.getProduct_category().getCategory_id()));
        logger.info("获取产品子信息-产品图片信息");
        List<ProductImage> productImageList = productImageService.getList(product_id, null);
        List<ProductImage> singleProductImageList = new ArrayList<>(5);
        List<ProductImage> detailsProductImageList = new ArrayList<>(8);
        for (ProductImage productImage : productImageList) {
            if (productImage.getProductImage_type() == 0) {
                singleProductImageList.add(productImage);
            } else {
                detailsProductImageList.add(productImage);
            }
        }
        product.setSingleProductImageList(singleProductImageList);
        product.setDetailProductImageList(detailsProductImageList);

        logger.info("获取产品子信息-销量数和评论数信息");
        product.setProduct_sale_count(productOrderItemService.getSaleCountByProductId(product_id));


        logger.info("获取猜你喜欢列表");
        Integer category_id = product.getProduct_category().getCategory_id();
        Integer total = productService.getTotal(new Product().setProduct_category(new Category().setCategory_id(category_id)), new Byte[]{0, 2});
        logger.info("分类ID为{}的产品总数为{}条", category_id, total);
        //生成随机数
        int i = new Random().nextInt(total);
        if (i + 2 >= total) {
            i = total - 3;
        }
        if (i < 0) {
            i = 0;
        }
        List<Product> loveProductList = productService.getList(new Product().setProduct_category(new Category().setCategory_id(category_id)), new Byte[]{0, 2}, null, new PageUtil().setCount(3).setPageStart(i));
        if (loveProductList != null) {
            logger.info("获取产品列表的相应的一张预览图片");
            for (Product loveProduct : loveProductList) {
                loveProduct.setSingleProductImageList(productImageService.getList(loveProduct.getProduct_id(), new PageUtil(0, 1)));
            }
        }
        logger.info("获取分类列表");
        List<Category> categoryList = categoryService.getList(null, new PageUtil(0, 3));

        map.put("loveProductList", loveProductList);
        map.put("categoryList", categoryList);
        map.put("product", product);
        map.put("guessNumber", i);
        map.put("pageUtil", new PageUtil(0, 10).setTotal(product.getProduct_review_count()));
        logger.info("转到前台-产品详情页");
        return "fore/productDetailsPage";
    }

    //按产品ID加载产品评论列表-ajax
    @Deprecated
    @ResponseBody
    @RequestMapping(value = "review/{pid}", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    public String loadProductReviewList(@PathVariable("pid") String pid/*产品ID*/,
                                        @RequestParam Integer index/* 页数 */,
                                        @RequestParam Integer count/* 行数 */) {
        logger.info("获取产品ID");
        Integer product_id = Integer.parseInt(pid);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("success", true);
        return jsonObject.toJSONString();
    }

    //按产品ID加载产品属性列表-ajax
    @Deprecated
    @ResponseBody
    @RequestMapping(value = "property/{pid}", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    public String loadProductPropertyList(@PathVariable("pid") String pid/*产品ID*/) {
        logger.info("获取产品ID");
        Integer product_id = Integer.parseInt(pid);

        logger.info("获取产品详情-属性值信息");
        Product product = new Product();
        product.setProduct_id(product_id);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("success", true);

        return jsonObject.toJSONString();
    }

    //加载猜你喜欢列表-ajax
    @ResponseBody
    @RequestMapping(value = "guess/{cid}", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    public String guessYouLike(@PathVariable("cid") Integer cid, @RequestParam Integer guessNumber) {
        logger.info("获取猜你喜欢列表");
        Integer total = productService.getTotal(new Product().setProduct_category(new Category().setCategory_id(cid)), new Byte[]{0, 2});
        logger.info("分类ID为{}的产品总数为{}条", cid, total);
        //生成随机数
        int i = new Random().nextInt(total);
        if (i + 2 >= total) {
            i = total - 3;
        }
        if (i < 0) {
            i = 0;
        }
        while (i == guessNumber) {
            i = new Random().nextInt(total);
            if (i + 2 >= total) {
                i = total - 3;
            }
            if (i < 0) {
                i = 0;
                break;
            }
        }

        logger.info("guessNumber值为{}，新guessNumber值为{}", guessNumber, i);
        List<Product> loveProductList = productService.getList(new Product().setProduct_category(new Category().setCategory_id(cid)), new Byte[]{0, 2}, null, new PageUtil().setCount(3).setPageStart(i));
        if (loveProductList != null) {
            logger.info("获取产品列表的相应的一张预览图片");
            for (Product loveProduct : loveProductList) {
                loveProduct.setSingleProductImageList(productImageService.getList(loveProduct.getProduct_id(), new PageUtil(0, 1)));
            }
        }

        JSONObject jsonObject = new JSONObject();
        logger.info("获取数据成功！");
        jsonObject.put("success", true);
        jsonObject.put("loveProductList", JSONArray.parseArray(JSON.toJSONString(loveProductList)));
        jsonObject.put("guessNumber", i);
        return jsonObject.toJSONString();
    }
}
