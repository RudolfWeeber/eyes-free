package com.google.marvin.config;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;

/**
 * InstallerActivity that displays the list of installable apps from the
 * Eyes-Free project.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class InstallerActivity extends Activity {

  private InstallerActivity self;

  private AppListView appList;
  private ProgressDialog loadingDialog = null;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    self = this;

    appList = new AppListView(this);

    setContentView(appList);
    getAppsList();
  }

  private void getAppsList() {
    loadingDialog = ProgressDialog.show(self, "Loading", "Please wait.", true);

    class appListLoader implements Runnable {
      public void run() {
        try {
          URLConnection cn;
          URL url = new URL("http://eyes-free.googlecode.com/svn/trunk/config/assets/applist.xml");
          cn = url.openConnection();
          cn.connect();
          InputStream stream = cn.getInputStream();
          DocumentBuilder docBuild = DocumentBuilderFactory.newInstance().newDocumentBuilder();
          Document appListDoc = docBuild.parse(stream);
          NodeList appNodes = appListDoc.getElementsByTagName("app");

          ArrayList<AppDesc> appDescs = new ArrayList<AppDesc>();
          for (int i = 0; i < appNodes.getLength(); i++) {
            NamedNodeMap attribs = appNodes.item(i).getAttributes();
            String packageName = attribs.getNamedItem("packageName").getNodeValue();
            String title = attribs.getNamedItem("title").getNodeValue();
            String description = attribs.getNamedItem("desc").getNodeValue();
            appDescs.add(new AppDesc(packageName, title, description));
          }

          appList.updateApps(appDescs);
          appList.postInvalidate();
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (SAXException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (ParserConfigurationException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (FactoryConfigurationError e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } finally {
          loadingDialog.dismiss();
        }
      }
    }
    Thread loadThread = (new Thread(new appListLoader()));
    loadThread.start();
  }





}
