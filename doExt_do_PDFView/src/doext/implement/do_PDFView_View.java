package doext.implement;

import java.util.Map;
import java.util.concurrent.Executor;

import org.json.JSONObject;

import android.content.Context;
import android.text.TextUtils;
import android.widget.RelativeLayout;

import com.artifex.mupdflib.AsyncTask;
import com.artifex.mupdflib.Hit;
import com.artifex.mupdflib.MuPDFAlert;
import com.artifex.mupdflib.MuPDFCore;
import com.artifex.mupdflib.MuPDFPageAdapter;
import com.artifex.mupdflib.MuPDFReaderView;
import com.artifex.mupdflib.MuPDFView;

import core.DoServiceContainer;
import core.helper.DoIOHelper;
import core.helper.DoJsonHelper;
import core.helper.DoUIModuleHelper;
import core.interfaces.DoBaseActivityListener;
import core.interfaces.DoIPageView;
import core.interfaces.DoIScriptEngine;
import core.interfaces.DoISourceFS;
import core.interfaces.DoIUIModuleView;
import core.object.DoInvokeResult;
import core.object.DoUIModule;
import doext.define.do_PDFView_IMethod;
import doext.define.do_PDFView_MAbstract;

/**
 * 自定义扩展UIView组件实现类，此类必须继承相应VIEW类，并实现DoIUIModuleView,do_PDFView_IMethod接口；
 * #如何调用组件自定义事件？可以通过如下方法触发事件：
 * this.model.getEventCenter().fireEvent(_messageName, jsonResult);
 * 参数解释：@_messageName字符串事件名称，@jsonResult传递事件参数对象； 获取DoInvokeResult对象方式new
 * DoInvokeResult(this.model.getUniqueKey());
 */
class ThreadPerTaskExecutor implements Executor {
	public void execute(Runnable r) {
		new Thread(r).start();
	}
}

public class do_PDFView_View extends RelativeLayout implements DoIUIModuleView, do_PDFView_IMethod, DoBaseActivityListener {
	/**
	 * 每个UIview都会引用一个具体的model实例；
	 */
	private do_PDFView_MAbstract model;
	private boolean mAlertsActive = false;
	private MuPDFCore core;
	private MuPDFReaderView mDocView;
	private AsyncTask<Void, Void, MuPDFAlert> mAlertTask;

	private boolean isTouchPageChanged = false;

	public void createAlertWaiter() {
		mAlertsActive = true;
		if (mAlertTask != null) {
			mAlertTask.cancel(true);
			mAlertTask = null;
		}
		mAlertTask = new AsyncTask<Void, Void, MuPDFAlert>() {

			@Override
			protected MuPDFAlert doInBackground(Void... arg0) {
				if (!mAlertsActive)
					return null;

				return core.waitForAlert();
			}

			@Override
			protected void onPostExecute(final MuPDFAlert result) {
				if (result == null)
					return;
			}
		};

		mAlertTask.executeOnExecutor(new ThreadPerTaskExecutor());
	}

	public void destroyAlertWaiter() {
		mAlertsActive = false;
		if (mAlertTask != null) {
			mAlertTask.cancel(true);
			mAlertTask = null;
		}
	}

	public do_PDFView_View(Context context) {
		super(context);
	}

	private RelativeLayout layout;

	/**
	 * 初始化加载view准备,_doUIModule是对应当前UIView的model实例
	 */
	@Override
	public void loadView(DoUIModule _doUIModule) throws Exception {
		this.model = (do_PDFView_MAbstract) _doUIModule;
		((DoIPageView) DoServiceContainer.getPageViewFactory().getAppContext()).setBaseActivityListener(this);
		layout = new RelativeLayout(DoServiceContainer.getPageViewFactory().getAppContext());
	}

	private void initPDF() {
		mDocView = new MuPDFReaderView(DoServiceContainer.getPageViewFactory().getAppContext()) {
			@Override
			protected void onMoveToChild(int i) {
				if (core == null)
					return;
				try {
					if (isTouchPageChanged) {
						OnPageChanged(i);
					}

				} catch (Exception e) {
					DoServiceContainer.getLogEngine().writeError("do_PDFView_View", e);
				}
				super.onMoveToChild(i);
			}

			@Override
			protected void onTapMainDocArea() {
			}

			@Override
			protected void onDocMotion() {
				isTouchPageChanged = true;
			}

			@Override
			protected void onHit(Hit item) {
				isTouchPageChanged = true;
			}

		};

	}

	private MuPDFCore openFile(String path) {
		int lastSlashPos = path.lastIndexOf('/');
		new String(lastSlashPos == -1 ? path : path.substring(lastSlashPos + 1));
		System.out.println("Trying to open " + path);
		try {
			core = new MuPDFCore(DoServiceContainer.getPageViewFactory().getAppContext(), path);
		} catch (Exception e) {
			DoServiceContainer.getLogEngine().writeError("do_PDFView_View", e);
			return null;
		}
		return core;
	}

	/**
	 * 动态修改属性值时会被调用，方法返回值为true表示赋值有效，并执行onPropertiesChanged，否则不进行赋值；
	 * 
	 * @_changedValues<key,value>属性集（key名称、value值）；
	 */
	@Override
	public boolean onPropertiesChanging(Map<String, String> _changedValues) {
		return true;
	}

	private String url;

	/**
	 * 属性赋值成功后被调用，可以根据组件定义相关属性值修改UIView可视化操作；
	 * 
	 * @_changedValues<key,value>属性集（key名称、value值）；
	 */
	@Override
	public void onPropertiesChanged(Map<String, String> _changedValues) {
		DoUIModuleHelper.handleBasicViewProperChanged(this.model, _changedValues);
		if (_changedValues.containsKey("url")) {
			url = _changedValues.get("url");
			try {
				String path = DoIOHelper.getLocalFileFullPath(this.model.getCurrentPage().getCurrentApp(), url);
				if (layout != null) {
					layout.removeView(mDocView);
					this.removeView(layout);
				}
				initPDF();
				if (checkFilePathValidate(url)) {
					isTouchPageChanged = false;
					setPDF(path);
				}
			} catch (Exception e) {
				DoServiceContainer.getLogEngine().writeError("do_PDFView_View", e);
			}

		}
	}

	public static boolean checkFilePathValidate(String _mFilePath) throws Exception {
		boolean _isVlidate = true;
		if (TextUtils.isEmpty(_mFilePath)) {
			throw new Exception("url 不能为空");
		}

		if (!_mFilePath.startsWith(DoISourceFS.DATA_PREFIX) && !_mFilePath.startsWith(DoISourceFS.SOURCE_PREFIX)) {
			_isVlidate = false;
			throw new Exception("url 只支持" + DoISourceFS.DATA_PREFIX + "、" + DoISourceFS.SOURCE_PREFIX + " 打头！");

		}
		return _isVlidate;
	}

	private void setPDF(String url) {
		core = openFile(url);
		mDocView.setAdapter(new MuPDFPageAdapter(DoServiceContainer.getPageViewFactory().getAppContext(), null, core));
		mDocView.setDisplayedViewIndex(0);
		layout.addView(mDocView);
		this.addView(layout);
	}

	/**
	 * 同步方法，JS脚本调用该组件对象方法时会被调用，可以根据_methodName调用相应的接口实现方法；
	 * 
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V），获取参数值使用API提供DoJsonHelper类；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public boolean invokeSyncMethod(String _methodName, JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		if ("getPageCount".equals(_methodName)) {
			this.getPageCount(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("next".equals(_methodName)) {
			this.next(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("prev".equals(_methodName)) {
			this.prev(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		if ("jump".equals(_methodName)) {
			this.jump(_dictParas, _scriptEngine, _invokeResult);
			return true;
		}
		return false;
	}

	/**
	 * 异步方法（通常都处理些耗时操作，避免UI线程阻塞），JS脚本调用该组件对象方法时会被调用， 可以根据_methodName调用相应的接口实现方法；
	 * 
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V），获取参数值使用API提供DoJsonHelper类；
	 * @_scriptEngine 当前page JS上下文环境
	 * @_callbackFuncName 回调函数名 #如何执行异步方法回调？可以通过如下方法：
	 *                    _scriptEngine.callback(_callbackFuncName,
	 *                    _invokeResult);
	 *                    参数解释：@_callbackFuncName回调函数名，@_invokeResult传递回调函数参数对象；
	 *                    获取DoInvokeResult对象方式new
	 *                    DoInvokeResult(this.model.getUniqueKey());
	 */
	@Override
	public boolean invokeAsyncMethod(String _methodName, JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) {
		return false;
	}

	/**
	 * 释放资源处理，前端JS脚本调用closePage或执行removeui时会被调用；
	 */
	@Override
	public void onDispose() {
		// ...do something
	}

	/**
	 * 重绘组件，构造组件时由系统框架自动调用；
	 * 或者由前端JS脚本调用组件onRedraw方法时被调用（注：通常是需要动态改变组件（X、Y、Width、Height）属性时手动调用）
	 */
	@Override
	public void onRedraw() {
		this.setLayoutParams(DoUIModuleHelper.getLayoutParams(this.model));
	}

	/**
	 * 获取当前model实例
	 */
	@Override
	public DoUIModule getModel() {
		return model;
	}

	/**
	 * 总共页数以及当前页数；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public void getPageCount(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		if (checkFilePathValidate(url)) {
			JSONObject _result = new JSONObject();
			_result.put("total", countPages());
			_result.put("current", muPDFview().getPage() + 1);
			_invokeResult.setResultNode(_result);
		}
	}

	/**
	 * 跳转到指定页；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public void jump(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		isTouchPageChanged = true;
		int _page = DoJsonHelper.getInt(_dictParas, "page", -1);
		if (checkFilePathValidate(url)) {
			// 当pdf文件只有一页的时候直接return 不触发pageChanged事件
			if (countPages() - 1 == 0) {
				return;
			}
			// 当_page参数小于0的时候 跳转到第一页
			if (_page <= 0) {
				jumpMethod(0);
			}
			// 当_page参数大于pdf文件总页数时 直接跳转到最后一页
			else if (_page >= countPages()) {
				jumpMethod(countPages() - 1);
			} else {
				// 正常跳转指定页数
				jumpMethod(_page - 1);
			}
		}
	}

	private void jumpMethod(int _page) {
		int currentPage = muPDFview().getPage();
		if (currentPage != _page)
			mDocView.setDisplayedViewIndex(_page);
	}

	/**
	 * 下一页；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public void next(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		isTouchPageChanged = true;
		if (checkFilePathValidate(url)) {
			int _page = muPDFview().getPage();
			_page++;
			if (_page >= 0 && _page < countPages()) {
				jumpMethod(_page);
			}

			if (_page == countPages()) {// TODO
				isTouchPageChanged = false;
			}
		}
	}

	/**
	 * 上一页；
	 * 
	 * @_dictParas 参数（K,V），可以通过此对象提供相关方法来获取参数值（Key：为参数名称）；
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public void prev(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		isTouchPageChanged = true;
		if (checkFilePathValidate(url)) {
			int _page = muPDFview().getPage();
			_page--;
			if (_page >= 0 && _page <= countPages()) {
				jumpMethod(_page);
			}
			if (isTouchPageChanged) {
				isTouchPageChanged = false;
			}
		}
	}

	private int countPages() throws Exception {
		int cPages = 0;
		if (checkFilePathValidate(url)) {
			cPages = core.countPages();
		}
		return cPages;
	}

	private MuPDFView muPDFview() {
		MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
		return pageView;
	}

	private void OnPageChanged(int pageIndex) throws Exception {
		DoInvokeResult _invokeResult = new DoInvokeResult(model.getUniqueKey());
		try {
			JSONObject json = new JSONObject();
			json.put("total", countPages());
			json.put("current", pageIndex + 1);
			_invokeResult.setResultNode(json);
			model.getEventCenter().fireEvent("pageChanged", _invokeResult);
		} catch (Exception e) {
			DoServiceContainer.getLogEngine().writeError("do_PDFView_View", e);
		}
	}

	@Override
	public void onResume() {
		if (core != null) {
			core.startAlerts();
			createAlertWaiter();
		}
	}

	@Override
	public void onPause() {

	}

	@Override
	public void onRestart() {

	}

	@Override
	public void onStop() {
		if (core != null) {
			destroyAlertWaiter();
			core.stopAlerts();
		}
	}
}