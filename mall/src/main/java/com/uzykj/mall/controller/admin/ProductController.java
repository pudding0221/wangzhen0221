package com.uzykj.mall.controller.admin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.qiniu.common.QiniuException;
import com.uzykj.mall.controller.BaseController;
import com.uzykj.mall.entity.*;
import com.uzykj.mall.service.*;
import com.uzykj.mall.util.FileIsExists;
import com.uzykj.mall.util.OrderUtil;
import com.uzykj.mall.util.PageUtil;
import com.uzykj.mall.util.qiniu.QiniuUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

/**
 * 后台管理-产品页
 */
@Controller
public class ProductController extends BaseController {
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductImageService productImageService;
    @Autowired
    private LastIDService lastIDService;

    //转到后台管理-产品页-ajax
    @RequestMapping(value = "admin/product", method = RequestMethod.GET)
    public String goToPage(HttpSession session, Map<String, Object> map) {
        logger.info("检查管理员权限");
        Object adminId = checkAdmin(session);
        if (adminId == null) {
            return "admin/include/loginMessage";
        }

        logger.info("获取产品分类列表");
        List<Category> categoryList = categoryService.getList(null, null);
        map.put("categoryList", categoryList);
        logger.info("获取前10条产品列表");
        PageUtil pageUtil = new PageUtil(0, 10);
        List<Product> productList = productService.getList(null, null, null, pageUtil);
        map.put("productList", productList);
        logger.info("获取产品总数量");
        Integer productCount = productService.getTotal(null, null);
        map.put("productCount", productCount);
        logger.info("获取分页信息");
        pageUtil.setTotal(productCount);
        map.put("pageUtil", pageUtil);

        logger.info("转到后台管理-产品页-ajax方式");
        return "admin/productManagePage";
    }

    //转到后台管理-产品详情页-ajax
    @RequestMapping(value = "admin/product/{pid}", method = RequestMethod.GET)
    public String goToDetailsPage(HttpSession session, Map<String, Object> map, @PathVariable Integer pid/* 产品ID */) {
        logger.info("检查管理员权限");
        Object adminId = checkAdmin(session);
        if (adminId == null) {
            return "admin/include/loginMessage";
        }

        logger.info("获取product_id为{}的产品信息", pid);
        Product product = productService.get(pid);
        logger.info("获取产品详情-图片信息");
        Integer product_id = product.getProduct_id();
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
        map.put("product", product);

        logger.info("获取分类列表");
        List<Category> categoryList = categoryService.getList(null, null);
        map.put("categoryList", categoryList);

        logger.info("转到后台管理-产品详情页-ajax方式");
        return "admin/include/productDetails";
    }

    //转到后台管理-产品添加页-ajax
    @RequestMapping(value = "admin/product/new", method = RequestMethod.GET)
    public String goToAddPage(HttpSession session, Map<String, Object> map) {
        logger.info("检查管理员权限");
        Object adminId = checkAdmin(session);
        if (adminId == null) {
            return "admin/include/loginMessage";
        }

        logger.info("获取分类列表");
        List<Category> categoryList = categoryService.getList(null, null);
        map.put("categoryList", categoryList);
        logger.info("获取第一个分类信息对应的属性列表");


        logger.info("转到后台管理-产品添加页-ajax方式");
        return "admin/include/productDetails";
    }

    //添加产品信息-ajax.
    @ResponseBody
    @RequestMapping(value = "admin/product", method = RequestMethod.POST, produces = "application/json;charset=utf-8")
    public String addProduct(@RequestParam String product_name/* 产品名称 */,
                             @RequestParam String product_title/* 产品标题 */,
                             @RequestParam Integer product_category_id/* 产品类型ID */,
                             @RequestParam Double product_sale_price/* 产品最低价 */,
                             @RequestParam Double product_price/* 产品最高价 */,
                             @RequestParam Byte product_isEnabled/* 产品状态 */,
                             @RequestParam String propertyJson/* 产品属性JSON */,
                             @RequestParam(required = false) String[] productSingleImageList/*产品预览图片名称数组*/,
                             @RequestParam(required = false) String[] productDetailsImageList/*产品详情图片名称数组*/) {
        JSONObject jsonObject = new JSONObject();
        logger.info("整合产品信息");
        Product product = new Product()
                .setProduct_name(product_name)
                .setProduct_title(product_title)
                .setProduct_category(new Category().setCategory_id(product_category_id))
                .setProduct_sale_price(product_sale_price)
                .setProduct_price(product_price)
                .setProduct_isEnabled(product_isEnabled)
                .setProduct_create_date(new Date());
        logger.info("添加产品信息");
        boolean yn = productService.add(product);
        if (!yn) {
            logger.warn("产品添加失败！事务回滚");
            jsonObject.put("success", false);
            throw new RuntimeException();
        }
        int product_id = lastIDService.selectLastID();
        logger.info("添加成功！,新增产品的ID值为：{}", product_id);

        JSONObject object = JSON.parseObject(propertyJson);
        Set<String> propertyIdSet = object.keySet();
        if (propertyIdSet.size() > 0) {
            logger.info("整合产品子信息-产品属性");
        }
        if (productSingleImageList != null && productSingleImageList.length > 0) {
            logger.info("整合产品子信息-产品预览图片");
            List<ProductImage> productImageList = new ArrayList<>(5);
            for (String imageName : productSingleImageList) {
                productImageList.add(new ProductImage()
                        .setProductImage_type((byte) 0)
                        .setProductImage_src(imageName.substring(imageName.lastIndexOf("/") + 1))
                        .setProductImage_product(new Product().setProduct_id(product_id))
                );
            }
            logger.info("共有{}条产品预览图片数据", productImageList.size());
            yn = productImageService.addList(productImageList);
            if (yn) {
                logger.info("产品预览图片添加成功！");
            } else {
                logger.warn("产品预览图片添加失败！事务回滚");
                jsonObject.put("success", false);
                throw new RuntimeException();
            }
        }

        if (productDetailsImageList != null && productDetailsImageList.length > 0) {
            logger.info("整合产品子信息-产品详情图片");
            List<ProductImage> productImageList = new ArrayList<>(5);
            for (String imageName : productDetailsImageList) {
                productImageList.add(new ProductImage()
                        .setProductImage_type((byte) 1)
                        .setProductImage_src(imageName.substring(imageName.lastIndexOf("/") + 1))
                        .setProductImage_product(new Product().setProduct_id(product_id))
                );
            }
            logger.info("共有{}条产品详情图片数据", productImageList.size());
            yn = productImageService.addList(productImageList);
            if (yn) {
                logger.info("产品详情图片添加成功！");
            } else {
                logger.warn("产品详情图片添加失败！事务回滚");
                jsonObject.put("success", false);
                throw new RuntimeException();
            }
        }
        logger.info("产品信息及其子信息添加成功！");
        jsonObject.put("success", true);
        jsonObject.put("product_id", product_id);

        return jsonObject.toJSONString();
    }

    //更新产品信息-ajax
    @ResponseBody
    @RequestMapping(value = "admin/product/{product_id}", method = RequestMethod.PUT, produces = "application/json;charset=utf-8")
    public String updateProduct(@RequestParam String product_name/* 产品名称 */,
                                @RequestParam String product_title/* 产品标题 */,
                                @RequestParam Integer product_category_id/* 产品类型ID */,
                                @RequestParam Double product_sale_price/* 产品最低价 */,
                                @RequestParam Double product_price/* 产品最高价 */,
                                @RequestParam Byte product_isEnabled/* 产品状态 */,
                                @RequestParam String propertyAddJson/* 产品添加属性JSON */,
                                @RequestParam String propertyUpdateJson/* 产品更新属性JSON */,
                                @RequestParam(required = false) Integer[] propertyDeleteList/* 产品删除属性ID数组 */,
                                @RequestParam(required = false) String[] productSingleImageList/*产品预览图片名称数组*/,
                                @RequestParam(required = false) String[] productDetailsImageList/*产品详情图片名称数组*/,
                                @PathVariable("product_id") Integer product_id/* 产品ID */) {
        JSONObject jsonObject = new JSONObject();
        logger.info("整合产品信息");
        Product product = new Product()
                .setProduct_id(product_id)
                .setProduct_name(product_name)
                .setProduct_title(product_title)
                .setProduct_category(new Category().setCategory_id(product_category_id))
                .setProduct_sale_price(product_sale_price)
                .setProduct_price(product_price)
                .setProduct_isEnabled(product_isEnabled)
                .setProduct_create_date(new Date());
        logger.info("更新产品信息，产品ID值为：{}", product_id);
        boolean yn = productService.update(product);
        if (!yn) {
            logger.info("产品信息更新失败！事务回滚");
            jsonObject.put("success", false);
            throw new RuntimeException();
        }
        logger.info("产品信息更新成功！");

        JSONObject object = JSON.parseObject(propertyAddJson);
        Set<String> propertyIdSet = object.keySet();

        if (productSingleImageList != null && productSingleImageList.length > 0) {
            logger.info("整合产品子信息-产品预览图片");
            List<ProductImage> productImageList = new ArrayList<>(5);
            for (String imageName : productSingleImageList) {
                productImageList.add(new ProductImage()
                        .setProductImage_type((byte) 0)
                        .setProductImage_src(imageName.substring(imageName.lastIndexOf("/") + 1))
                        .setProductImage_product(product)
                );
            }
            logger.info("共有{}条产品预览图片数据", productImageList.size());
            yn = productImageService.addList(productImageList);
            if (yn) {
                logger.info("产品预览图片添加成功！");
            } else {
                logger.warn("产品预览图片添加失败！事务回滚");
                jsonObject.put("success", false);
                throw new RuntimeException();
            }
        }
        if (productDetailsImageList != null && productDetailsImageList.length > 0) {
            logger.info("整合产品子信息-产品详情图片");
            List<ProductImage> productImageList = new ArrayList<>(5);
            for (String imageName : productDetailsImageList) {
                productImageList.add(new ProductImage()
                        .setProductImage_type((byte) 1)
                        .setProductImage_src(imageName)
                        .setProductImage_product(product)
                );
            }
            logger.info("共有{}条产品详情图片数据", productImageList.size());
            yn = productImageService.addList(productImageList);
            if (yn) {
                logger.info("产品详情图片添加成功！");
            } else {
                logger.warn("产品详情图片添加失败！事务回滚");
                jsonObject.put("success", false);
                throw new RuntimeException();
            }
        }
        jsonObject.put("success", true);
        jsonObject.put("product_id", product_id);

        return jsonObject.toJSONString();
    }

    //删除商品
    @ResponseBody
    @RequestMapping(value = "admin/product/delete/{arr}", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    public String deleteProduct(@PathVariable("arr") Integer[] product_id_list/* 商品id集合 */) {
        JSONObject object = new JSONObject();
        logger.info("删除:用户id数组：" + product_id_list.toString());
        for (int i = 0; i < product_id_list.length; i++) {



            //删除商品图片
            List<ProductImage> imageList = productImageService.getList(product_id_list[i], null);
            if (imageList != null && imageList.size() != 0) {
                Integer[] productImage_id_list = new Integer[imageList.size()];
                for (int k = 0; k < imageList.size(); k++) {
                    productImage_id_list[k] = imageList.get(k).getProductImage_id();
                    try {
                        String[] split = imageList.get(k).getProductImage_src().split("/");
                        QiniuUtil.delete(split[split.length - 1], QiniuUtil.MALL_ZONE);
                    } catch (QiniuException e) {
                        logger.info("七牛云图片资源删除失败！");
                        e.printStackTrace();
                    }
                }
                if (productImageService.delete(productImage_id_list)) {
                    logger.info("已删除商品图片表数据：" + productImage_id_list.length + "条");
                } else {
                    throw new RuntimeException("删除商品图片表数据异常");
                }
            } else {
                logger.info("没有商品图片数据");
                object.put("success", false);
            }
        }

        if (productService.delete(product_id_list)) {
            object.put("success", true);
            logger.info("已删除商品：" + product_id_list.length + "条");
        } else {
            object.put("success", false);
        }
        return object.toJSONString();
    }

    //按条件查询产品-ajax
    @ResponseBody
    @RequestMapping(value = "admin/product/{index}/{count}", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    public String getProductBySearch(@RequestParam(required = false) String product_name/* 产品名称 */,
                                     @RequestParam(required = false) Integer category_id/* 产品类型ID */,
                                     @RequestParam(required = false) Double product_sale_price/* 产品最低价 */,
                                     @RequestParam(required = false) Double product_price/* 产品最高价 */,
                                     @RequestParam(required = false) Byte[] product_isEnabled_array/* 产品状态数组 */,
                                     @RequestParam(required = false) String orderBy/* 排序字段 */,
                                     @RequestParam(required = false, defaultValue = "true") Boolean isDesc/* 是否倒序 */,
                                     @PathVariable Integer index/* 页数 */,
                                     @PathVariable Integer count/* 行数 */) throws UnsupportedEncodingException {
        //移除不必要条件
        if (product_isEnabled_array != null && (product_isEnabled_array.length <= 0 || product_isEnabled_array.length >= 3)) {
            product_isEnabled_array = null;
        }
        if (category_id != null && category_id == 0) {
            category_id = null;
        }
        if (product_name != null) {
            //如果为非空字符串则解决中文乱码：URLDecoder.decode(String,"UTF-8");
            product_name = product_name.equals("") ? null : URLDecoder.decode(product_name, "UTF-8");
        }
        if (orderBy != null && orderBy.equals("")) {
            orderBy = null;
        }
        //封装查询条件
        Product product = new Product()
                .setProduct_name(product_name)
                .setProduct_category(new Category().setCategory_id(category_id))
                .setProduct_price(product_price)
                .setProduct_sale_price(product_sale_price);
        OrderUtil orderUtil = null;
        if (orderBy != null) {
            logger.info("根据{}排序，是否倒序:{}", orderBy, isDesc);
            orderUtil = new OrderUtil(orderBy, isDesc);
        }

        JSONObject object = new JSONObject();
        logger.info("按条件获取第{}页的{}条产品", index + 1, count);
        PageUtil pageUtil = new PageUtil(index, count);
        List<Product> productList = productService.getList(product, product_isEnabled_array, orderUtil, pageUtil);
        object.put("productList", JSONArray.parseArray(JSON.toJSONString(productList)));
        logger.info("按条件获取产品总数量");
        Integer productCount = productService.getTotal(product, product_isEnabled_array);
        object.put("productCount", productCount);
        logger.info("获取分页信息");
        pageUtil.setTotal(productCount);
        object.put("totalPage", pageUtil.getTotalPage());
        object.put("pageUtil", pageUtil);

        return object.toJSONString();
    }

    //按类型ID查询属性-ajax
    @ResponseBody
    @RequestMapping(value = "admin/property/type/{property_category_id}", method = RequestMethod.GET, produces = "application/json;charset=utf-8")
    public String getPropertyByCategoryId(@PathVariable Integer property_category_id/* 属性所属类型ID*/) {
        //封装查询条件
        Category category = new Category()
                .setCategory_id(property_category_id);

        JSONObject object = new JSONObject();
        return object.toJSONString();
    }

    //按ID删除产品图片并返回最新结果-ajax
    @ResponseBody
    @RequestMapping(value = "admin/productImage/{productImage_id}", method = RequestMethod.DELETE, produces = "application/json;charset=utf-8")
    public String deleteProductImageById(@PathVariable Integer productImage_id/* 产品图片ID */) {
        JSONObject object = new JSONObject();
        logger.info("获取productImage_id为{}的产品图片信息", productImage_id);
        @SuppressWarnings("unused")
        ProductImage productImage = productImageService.get(productImage_id);
        Boolean yn = false;
        try {
            logger.info("删除产品图片");
            QiniuUtil.delete(productImage.getProductImage_src(), QiniuUtil.MALL_ZONE);
            yn = productImageService.delete(new Integer[]{productImage_id});
        } catch (QiniuException e) {
            e.printStackTrace();
            logger.info("七牛云图片删除失败！");
        }
        if (yn) {
            logger.info("删除图片成功！");
            object.put("success", true);
        } else {
            logger.warn("删除图片失败！事务回滚");
            object.put("success", false);
            throw new RuntimeException();
        }
        return object.toJSONString();
    }

    //上传产品图片-ajax
    @ResponseBody
    @RequestMapping(value = "admin/uploadProductImage", method = RequestMethod.POST, produces = "application/json;charset=utf-8")
    public String uploadProductImage(@RequestParam MultipartFile file, @RequestParam String imageType, HttpSession session) {
        JSONObject object = new JSONObject();
        if (!file.isEmpty()) {
            String originalFileName = file.getOriginalFilename();
            if (QiniuUtil.IS_ENABLE.equals("true")) {
                try {
                    logger.info("文件上传中...");
                    UpResult upload = QiniuUtil.upload(file.getInputStream(), originalFileName, QiniuUtil.MALL_ZONE);
                    if (upload != null) {
                        logger.info("七牛云路径：", upload.zoneName + upload.fileName);
                        logger.info("文件上传完成");
                        object.put("success", true);
                        String fileUrl = QiniuUtil.getFileUrl(upload.fileName, QiniuUtil.MALL_DOMAIN);
                        object.put("fileUrl", fileUrl);
                    } else {
                        logger.info("文件上传失败！");
                        object.put("success", false);
                    }
                } catch (IOException e) {
                    logger.warn("文件上传失败！", e);
                    object.put("success", false);
                }
            } else if (QiniuUtil.IS_ENABLE.equals("false")) {
                logger.info("获取图片原始文件名：{}", originalFileName);
                // 转存文件
                FileIsExists.createDirectory(QiniuUtil.LOCAL_FILE_PATH);
                try {
                    file.transferTo(new File(QiniuUtil.LOCAL_FILE_PATH + originalFileName));
                } catch (IOException e) {
                    logger.warn("文件上传失败！", e);
                    object.put("success", false);
                }
                object.put("success", true);
                String fileUrl = QiniuUtil.LOCAL_FILE_PREFIX + "res/images/fore/store/" + originalFileName;
                object.put("fileUrl", fileUrl);
            }
        }
        return object.toJSONString();
    }
}