/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.configuration.admin.web.portlet.action;

import com.liferay.configuration.admin.web.constants.ConfigurationAdminPortletKeys;
import com.liferay.configuration.admin.web.model.ConfigurationModel;
import com.liferay.configuration.admin.web.util.AttributeDefinitionUtil;
import com.liferay.configuration.admin.web.util.ConfigurationModelRetriever;
import com.liferay.portal.kernel.portlet.PortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCResourceCommand;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PropertiesUtil;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.metatype.definitions.ExtendedAttributeDefinition;
import com.liferay.portal.theme.ThemeDisplay;

import java.util.Map;
import java.util.Properties;

import javax.portlet.MimeResponse;
import javax.portlet.PortletException;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;

import org.osgi.service.cm.Configuration;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.AttributeDefinition;

/**
 * @author Brian Wing Shun Chan
 * @author Vilmos Papp
 * @author Eduardo Garcia
 * @author Raymond Augé
 */
@Component(
	immediate = true,
	property = {
		"javax.portlet.name=" +
			ConfigurationAdminPortletKeys.CONFIGURATION_ADMIN,
		"mvc.command.name=export"
	},
	service = MVCResourceCommand.class
)
public class ExportConfigurationMVCResourceCommand
	implements MVCResourceCommand {

	@Override
	public boolean serveResource(
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws PortletException {

		if (!(resourceResponse instanceof MimeResponse)) {
			return false;
		}

		try {
			exportPid(resourceRequest, resourceResponse);
		}
		catch (Exception e) {
			throw new PortletException(e);
		}

		return true;
	}

	protected void exportPid(
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws Exception {

		String factoryPid = ParamUtil.getString(resourceRequest, "factoryPid");
		String pid = ParamUtil.getString(resourceRequest, "pid");

		ThemeDisplay themeDisplay = (ThemeDisplay)resourceRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		String languageId = themeDisplay.getLanguageId();

		String fileName = getFileName(factoryPid, pid);

		PortletResponseUtil.sendFile(
			resourceRequest, resourceResponse, fileName,
			getPropertiesAsBytes(languageId, factoryPid, pid),
			ContentTypes.TEXT_XML_UTF8);
	}

	private String getFileName(String factoryPid, String pid) {
		String fileName = pid;

		if (Validator.isNotNull(factoryPid) && !factoryPid.equals(pid)) {
			String factoryInstanceId = pid.substring(factoryPid.length() + 1);

			fileName = factoryPid + StringPool.DASH + factoryInstanceId;
		}

		return fileName + ".cfg";
	}

	protected Properties getProperties(
			String languageId, String factoryPid, String pid)
		throws Exception {

		Properties properties = new Properties();

		Map<String, ConfigurationModel> configurationModels =
			_configurationModelRetriever.getConfigurationModels(languageId);

		ConfigurationModel configurationModel = configurationModels.get(pid);

		if ((configurationModel == null) && Validator.isNotNull(factoryPid)) {
			configurationModel = configurationModels.get(factoryPid);
		}

		if (configurationModel == null) {
			return properties;
		}

		Configuration configuration =
			_configurationModelRetriever.getConfiguration(pid);

		if (configuration == null) {
			return properties;
		}

		ExtendedAttributeDefinition[] attributeDefinitions =
			configurationModel.getAttributeDefinitions(ConfigurationModel.ALL);

		for (AttributeDefinition attributeDefinition : attributeDefinitions) {
			String[] values = AttributeDefinitionUtil.getProperty(
				attributeDefinition, configuration);

			String value = null;

			// See http://goo.gl/JhYK7g

			if (values.length == 1) {
				value = values[0];
			}
			else if (values.length > 1) {
				value = StringUtil.merge(values, "\n");
			}

			properties.setProperty(attributeDefinition.getID(), value);
		}

		return properties;
	}

	protected byte[] getPropertiesAsBytes(
			String languageId, String factoryPid, String pid)
		throws Exception {

		StringBundler sb = new StringBundler(5);

		sb.append("##\n## To apply the configuration, deploy this file to a ");
		sb.append("Liferay installation with the file name ");
		sb.append(getFileName(factoryPid, pid));
		sb.append(".\n##\n\n");

		Properties properties = getProperties(languageId, factoryPid, pid);

		sb.append(PropertiesUtil.toString(properties));

		String propertiesString = sb.toString();

		return propertiesString.getBytes();
	}

	@Reference(unbind = "-")
	protected void setConfigurationModelRetriever(
		ConfigurationModelRetriever configurationModelRetriever) {

		_configurationModelRetriever = configurationModelRetriever;
	}

	private ConfigurationModelRetriever _configurationModelRetriever;

}