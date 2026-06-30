package com.catalogue.verg.core.util;

/**
 * @author Mahesh RV
 * @author Ruksana
 */
public class ApiRespParam {
    private String resMsgId;
    private String msgId;
    private String err;
    private String status;
    private String errMsg;

    public String getResMsgId() {
        return resMsgId;
    }

    public void setResMsgId(String resMsgId) {
        this.resMsgId = resMsgId;
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getErr() {
        return err;
    }

    public void setErr(String err) {
        this.err = err;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public ApiRespParam() {
    }

    public ApiRespParam(String id) {
        resMsgId = id;
        msgId = id;
    }

}