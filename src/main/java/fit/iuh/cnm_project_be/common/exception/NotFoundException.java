package fit.iuh.cnm_project_be.common.exception;

import fit.iuh.cnm_project_be.common.error.ErrorCode;

public class NotFoundException extends ApiException {

    public NotFoundException() {
        super(ErrorCode.NOT_FOUND);
    }

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}