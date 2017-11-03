package doext.define;

import core.object.DoProperty;
import core.object.DoProperty.PropertyDataType;
import core.object.DoUIModule;

public abstract class do_PDFView_MAbstract extends DoUIModule {

	protected do_PDFView_MAbstract() throws Exception {
		super();
	}

	/**
	 * 初始化
	 */
	@Override
	public void onInit() throws Exception {
		super.onInit();
		// 注册属性
		this.registProperty(new DoProperty("url", PropertyDataType.String, "", false));
	}

}