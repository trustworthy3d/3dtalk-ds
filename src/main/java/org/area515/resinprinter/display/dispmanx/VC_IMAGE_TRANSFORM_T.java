package org.area515.resinprinter.display.dispmanx;

public enum VC_IMAGE_TRANSFORM_T {
	VC_IMAGE_ROT0(0),
	VC_IMAGE_MIRROR_ROT0(TRANSFORM.TRANSFORM_HFLIP.getcConst()),
	VC_IMAGE_MIRROR_ROT180(TRANSFORM.TRANSFORM_VFLIP.getcConst()),
	VC_IMAGE_ROT180(TRANSFORM.TRANSFORM_HFLIP.getcConst()|TRANSFORM.TRANSFORM_VFLIP.getcConst()),
	VC_IMAGE_MIRROR_ROT90(TRANSFORM.TRANSFORM_TRANSPOSE.getcConst()),
	VC_IMAGE_ROT270(TRANSFORM.TRANSFORM_TRANSPOSE.getcConst()|TRANSFORM.TRANSFORM_HFLIP.getcConst()),
	VC_IMAGE_ROT90(TRANSFORM.TRANSFORM_TRANSPOSE.getcConst()|TRANSFORM.TRANSFORM_VFLIP.getcConst()),
	VC_IMAGE_MIRROR_ROT270(TRANSFORM.TRANSFORM_TRANSPOSE.getcConst()|TRANSFORM.TRANSFORM_HFLIP.getcConst()|TRANSFORM.TRANSFORM_VFLIP.getcConst());
	private int cConst;

	VC_IMAGE_TRANSFORM_T(int cConst) {
		this.cConst = cConst;
	}

	public int getcConst() {
		return cConst;
	}
}