// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.sdk.internal.wip;

import org.chromium.sdk.JavascriptVm;
import org.chromium.sdk.internal.wip.protocol.input.page.FrameNavigatedEventData;
import org.chromium.sdk.internal.wip.protocol.input.page.FrameValue;
import org.chromium.sdk.internal.wip.protocol.input.page.GetResourceTreeData;
import org.chromium.sdk.internal.wip.protocol.output.page.GetResourceTreeParams;

/**
 * Collects information about frame tree. At first class only watches for the url of root frame.
 */
class WipFrameManager {
  private final WipTabImpl tabImpl;
  private boolean urlUnknown = true;

  WipFrameManager(WipTabImpl tabImpl) {
    this.tabImpl = tabImpl;
  }

  void readFrames() {
    GetResourceTreeParams requestParams = new GetResourceTreeParams();
    JavascriptVm.GenericCallback<GetResourceTreeData> callback =
        new JavascriptVm.GenericCallback<GetResourceTreeData>() {
          @Override
          public void success(GetResourceTreeData value) {
            String url = value.frameTree().frame().url();
            boolean silentUpdate = urlUnknown;
            tabImpl.updateUrl(url, silentUpdate);
            urlUnknown = false;
          }

          @Override public void failure(Exception exception) {
            throw new RuntimeException("Failed to read frame data", exception);
          }
        };

    tabImpl.getCommandProcessor().send(requestParams, callback, null);
  }

  void frameNavigated(FrameNavigatedEventData eventData) {
    FrameValue frame = eventData.frame();
    String parentId = frame.parentId();
    if ("".equals(parentId)) {
      String newUrl = frame.url();
      tabImpl.updateUrl(newUrl, false);
      urlUnknown = false;
    }
  }
}
