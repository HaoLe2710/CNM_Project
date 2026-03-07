package fit.iuh.cnm_project_be.common.exception;

import fit.iuh.cnm_project_be.common.error.ErrorCode;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED);
    }

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}