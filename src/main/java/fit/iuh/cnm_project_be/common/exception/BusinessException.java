package fit.iuh.cnm_project_be.common.exception;

import fit.iuh.cnm_project_be.common.error.ErrorCode;

public class BusinessException extends ApiException {

    public BusinessException() {
        super(ErrorCode.BUSINESS_ERROR);
    }

    public BusinessException(String message) {
        super(ErrorCode.BUSINESS_ERROR, message);
    }
}