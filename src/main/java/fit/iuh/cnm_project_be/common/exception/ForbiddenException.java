package fit.iuh.cnm_project_be.common.exception;

import fit.iuh.cnm_project_be.common.error.ErrorCode;

public class ForbiddenException extends ApiException {

    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}