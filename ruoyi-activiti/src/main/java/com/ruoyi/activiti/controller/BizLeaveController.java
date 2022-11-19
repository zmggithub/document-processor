package com.ruoyi.activiti.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.activiti.service.IProcessService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.config.Global;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.framework.util.ShiroUtils;
import com.ruoyi.activiti.domain.BizLeaveVo;
import com.ruoyi.activiti.service.IBizLeaveService;
import com.ruoyi.system.domain.SysUser;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.repository.Model;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * 请假业务Controller
 *
 * @author Xianlu Tech
 * @date 2019-10-11
 */
@Controller
@RequestMapping("/leave")
public class BizLeaveController extends BaseController {
    private String prefix = "leave";
    private static final Logger log = LoggerFactory.getLogger(BizLeaveController.class);

    @Autowired
    private IBizLeaveService bizLeaveService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private IProcessService processService;

    @GetMapping()
    public String leave(ModelMap mmap) {
        mmap.put("currentUser", ShiroUtils.getSysUser());
        return prefix + "/leave";
    }

    /**
     * 查询请假业务列表
     */
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(BizLeaveVo bizLeave) {
        if (!SysUser.isAdmin(ShiroUtils.getUserId())) {
            bizLeave.setCreateBy(ShiroUtils.getLoginName());
        }
        bizLeave.setType("leave");
        startPage();
        List<BizLeaveVo> list = bizLeaveService.selectBizLeaveList(bizLeave);
        return getDataTable(list);
    }

    /**
     * 导出请假业务列表
     */
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(BizLeaveVo bizLeave) {
        bizLeave.setType("leave");
        List<BizLeaveVo> list = bizLeaveService.selectBizLeaveList(bizLeave);
        ExcelUtil<BizLeaveVo> util = new ExcelUtil<BizLeaveVo>(BizLeaveVo.class);
        return util.exportExcel(list, "leave");
    }

    /**
     * 新增请假业务
     */
    @GetMapping("/add")
    public String add() {
        return prefix + "/add";
    }

    /**
     * 新增保存请假业务
     */
    @Log(title = "请假业务", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(BizLeaveVo bizLeave) {
        Long userId = ShiroUtils.getUserId();
        if (SysUser.isAdmin(userId)) {
            return error("提交申请失败：不允许管理员提交申请！");
        }
        bizLeave.setType("leave");
        return toAjax(bizLeaveService.insertBizLeave(bizLeave));
    }

    /**
     * 修改请假业务
     */
    @GetMapping("/edit/{id}")
    public String edit(@PathVariable("id") Long id, ModelMap mmap) {
        BizLeaveVo bizLeave = bizLeaveService.selectBizLeaveById(id);
        mmap.put("bizLeave", bizLeave);
        return prefix + "/edit";
    }

    /**
     * 修改保存请假业务
     */
    @Log(title = "请假业务", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(BizLeaveVo bizLeave) {
        return toAjax(bizLeaveService.updateBizLeave(bizLeave));
    }

    /**
     * 删除请假业务
     */
    @Log(title = "请假业务", businessType = BusinessType.DELETE)
    @PostMapping( "/remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(bizLeaveService.deleteBizLeaveByIds(ids));
    }

    /**
     * 提交申请
     */
    @Log(title = "请假业务", businessType = BusinessType.UPDATE)
    @PostMapping( "/submitApply")
    @ResponseBody
    public AjaxResult submitApply(Long id) {
        BizLeaveVo leave = bizLeaveService.selectBizLeaveById(id);
        String applyUserId = ShiroUtils.getLoginName();
        bizLeaveService.submitApply(leave, applyUserId, "leave", new HashMap<>());
        return success();
    }

    @GetMapping("/leaveTodo")
    public String todoView() {
        return prefix + "/leaveTodo";
    }

    /**
     * 我的待办列表
     * @return
     */
    @PostMapping("/taskList")
    @ResponseBody
    public TableDataInfo taskList(BizLeaveVo bizLeave) {
        bizLeave.setType("leave");
        List<BizLeaveVo> list = bizLeaveService.findTodoTasks(bizLeave, ShiroUtils.getLoginName());
        return getDataTable(list);
    }

    /**
     * 加载审批弹窗
     * @param taskId
     * @param mmap
     * @return
     */
    @GetMapping("/showVerifyDialog/{taskId}")
    public String showVerifyDialog(@PathVariable("taskId") String taskId, ModelMap mmap) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        String processInstanceId = task.getProcessInstanceId();
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        BizLeaveVo bizLeave = bizLeaveService.selectBizLeaveById(new Long(processInstance.getBusinessKey()));
        mmap.put("bizLeave", bizLeave);
        mmap.put("taskId", taskId);
        String verifyName = task.getTaskDefinitionKey().substring(0, 1).toUpperCase() + task.getTaskDefinitionKey().substring(1);
        return prefix + "/task" + verifyName;
    }

    @GetMapping("/showFormDialog/{instanceId}")
    public String showFormDialog(@PathVariable("instanceId") String instanceId, ModelMap mmap) {
        String businessKey = processService.findBusinessKeyByInstanceId(instanceId);
        BizLeaveVo bizLeaveVo = bizLeaveService.selectBizLeaveById(new Long(businessKey));
        mmap.put("bizLeave", bizLeaveVo);
        return prefix + "/view";
    }

    /**
     * 完成任务
     *
     * @return
     */
    @RequestMapping(value = "/complete/{taskId}", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public AjaxResult complete(@PathVariable("taskId") String taskId, @RequestParam(value = "saveEntity", required = false) String saveEntity,
                               @ModelAttribute("preloadLeave") BizLeaveVo leave, HttpServletRequest request) {
        boolean saveEntityBoolean = BooleanUtils.toBoolean(saveEntity);
        processService.complete(taskId, leave.getInstanceId(), leave.getTitle(), leave.getReason(), "leave", new HashMap<String, Object>(), request);
        if (saveEntityBoolean) {
            bizLeaveService.updateBizLeave(leave);
        }
        return success("任务已完成");
    }

    /**
     * 自动绑定页面字段
     */
    @ModelAttribute("preloadLeave")
    public BizLeaveVo getLeave(@RequestParam(value = "id", required = false) Long id, HttpSession session) {
        if (id != null) {
            return bizLeaveService.selectBizLeaveById(id);
        }
        return new BizLeaveVo();
    }

    @GetMapping("/leaveDone")
    public String doneView() {
        return prefix + "/leaveDone";
    }

    /**
     * 我的已办列表
     * @param bizLeave
     * @return
     */
    @PostMapping("/taskDoneList")
    @ResponseBody
    public TableDataInfo taskDoneList(BizLeaveVo bizLeave) {
        bizLeave.setType("leave");
        List<BizLeaveVo> list = bizLeaveService.findDoneTasks(bizLeave, ShiroUtils.getLoginName());
        return getDataTable(list);
    }



    /**
     * 上传附件
     */
    @Log(title = "上传附件")
    @PostMapping( "/upload")
    @ResponseBody
    public AjaxResult upload(@RequestParam("leaveUploadAccessory") MultipartFile file) {
        try {
            if (!file.isEmpty()) {
                // String extensionName = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.') + 1);
                // if (!"bpmn".equalsIgnoreCase(extensionName)
                //         && !"zip".equalsIgnoreCase(extensionName)
                //         && !"bar".equalsIgnoreCase(extensionName)) {
                //     return error("流程定义文件仅支持 bpmn, zip 和 bar 格式！");
                // }
                // p.s. 此时 FileUploadUtils.upload() 返回字符串 fileName 前缀为 Constants.RESOURCE_PREFIX，需剔除
                // 详见: FileUploadUtils.getPathFileName(...)
                String fileName = FileUploadUtils.upload(Global.getProfile() + "/leaveUploadAccessory", file);
                if (StringUtils.isNotBlank(fileName)) {
                    String realFilePath = Global.getProfile() + fileName.substring(Constants.RESOURCE_PREFIX.length());
                    return success(realFilePath);
                }
            }
            return error("不允许上传空文件！");
        }
        catch (Exception e) {
            log.error("上传流程定义文件失败！", e);
            return error(e.getMessage());
        }
    }

    /**
     * 导出model的xml文件
     */
    @RequestMapping(value = "/export/{id}")
    public void export(@PathVariable("id") Long id, HttpServletResponse response, HttpServletRequest request) {
        try {
            BizLeaveVo bizLeaveVo = bizLeaveService.selectBizLeaveById(id);


            String filePath = bizLeaveVo.getFilePath();

            response.setCharacterEncoding("utf-8");
            response.setContentType("multipart/form-data");
            response.setHeader("Content-Disposition",
                    "attachment;fileName=" + FileUtils.setFileDownloadHeader(request, bizLeaveVo.getFileName()));
            FileUtils.writeBytes(filePath, response.getOutputStream());

        } catch (Exception e) {
            log.error("导出model的xml文件失败：id={}", id, e);
            try {
                response.sendError(500 , "文件失败下载失败");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
