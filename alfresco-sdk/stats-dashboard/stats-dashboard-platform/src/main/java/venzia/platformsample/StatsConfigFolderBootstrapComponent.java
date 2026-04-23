package venzia.platformsample;

import java.io.InputStream;
import java.util.List;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.evaluator.CompareMimeTypeEvaluator;
import org.alfresco.repo.action.executer.SpecialiseTypeActionExecuter;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.module.AbstractModuleComponent;
import org.alfresco.repo.nodelocator.NodeLocatorService;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ActionCondition;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.rule.Rule;
import org.alfresco.service.cmr.rule.RuleService;
import org.alfresco.service.cmr.rule.RuleType;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * One-time bootstrap component that ensures Data Dictionary/Stats Config exists.
 */
public class StatsConfigFolderBootstrapComponent extends AbstractModuleComponent {
    private static final Log logger = LogFactory.getLog(StatsConfigFolderBootstrapComponent.class);

    private static final String DATA_DICTIONARY_NAME = "Data Dictionary";
    private static final String STATS_CONFIG_FOLDER_NAME = "Stats Config";
    private static final String STATS_CONFIG_RULE_TITLE = "Auto-specialise JSON to venzia:statsConfig";
    private static final String DEFAULT_STATS_CONFIG_NAME = "default-repository-stats.json";
    private static final String DEFAULT_STATS_CONFIG_RESOURCE = "alfresco/module/stats-dashboard-platform/content/default-repository-stats.json";
    private static final QName STATS_CONFIG_TYPE = QName.createQName("http://www.venzia.es/model/content/1.0", "statsConfig");

    private NodeService nodeService;
    private NodeLocatorService nodeLocatorService;
    private FileFolderService fileFolderService;
    private RuleService ruleService;
    private ActionService actionService;

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setNodeLocatorService(NodeLocatorService nodeLocatorService) {
        this.nodeLocatorService = nodeLocatorService;
    }

    public void setFileFolderService(FileFolderService fileFolderService) {
        this.fileFolderService = fileFolderService;
    }

    public void setRuleService(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    public void setActionService(ActionService actionService) {
        this.actionService = actionService;
    }

    @Override
    protected void executeInternal() throws Throwable {
        NodeRef companyHome = nodeLocatorService.getNode("companyhome", null, null);
        if (companyHome == null) {
            logger.warn("Company Home not found. Cannot bootstrap Stats Config folder.");
            return;
        }

        NodeRef dataDictionary = nodeService.getChildByName(companyHome, ContentModel.ASSOC_CONTAINS, DATA_DICTIONARY_NAME);
        if (dataDictionary == null) {
            logger.warn("Data Dictionary folder not found. Cannot bootstrap Stats Config folder.");
            return;
        }

        NodeRef statsConfigFolder = nodeService.getChildByName(dataDictionary, ContentModel.ASSOC_CONTAINS, STATS_CONFIG_FOLDER_NAME);
        if (statsConfigFolder == null) {
            statsConfigFolder = fileFolderService.create(dataDictionary, STATS_CONFIG_FOLDER_NAME, ContentModel.TYPE_FOLDER).getNodeRef();
            logger.info("Created bootstrap folder Data Dictionary/Stats Config");
        } else {
            logger.debug("Stats Config folder already exists under Data Dictionary.");
        }

        ensureStatsConfigRule(statsConfigFolder);
        ensureDefaultStatsConfig(statsConfigFolder);
    }

    private void ensureStatsConfigRule(NodeRef statsConfigFolder) {
        List<Rule> existingRules = ruleService.getRules(statsConfigFolder, false);
        for (Rule existingRule : existingRules) {
            if (STATS_CONFIG_RULE_TITLE.equals(existingRule.getTitle())) {
                logger.debug("Stats Config bootstrap rule already exists.");
                return;
            }
        }

        Action specialiseTypeAction = actionService.createAction(SpecialiseTypeActionExecuter.NAME);
        specialiseTypeAction.setParameterValue(SpecialiseTypeActionExecuter.PARAM_TYPE_NAME, STATS_CONFIG_TYPE);

        ActionCondition jsonMimeTypeCondition = actionService.createActionCondition(CompareMimeTypeEvaluator.NAME);
        jsonMimeTypeCondition.setParameterValue(CompareMimeTypeEvaluator.PARAM_VALUE, "application/json");
        specialiseTypeAction.addActionCondition(jsonMimeTypeCondition);

        Rule rule = new Rule();
        rule.setTitle(STATS_CONFIG_RULE_TITLE);
        rule.setDescription("Specialise uploaded JSON files to venzia:statsConfig");
        rule.setRuleType(RuleType.INBOUND);
        rule.setAction(specialiseTypeAction);
        rule.applyToChildren(true);
        rule.setExecuteAsynchronously(false);
        rule.setRuleDisabled(false);

        ruleService.saveRule(statsConfigFolder, rule);
        logger.info("Created bootstrap rule on Data Dictionary/Stats Config to specialise JSON files to venzia:statsConfig");
    }

    private void ensureDefaultStatsConfig(NodeRef statsConfigFolder) {
        NodeRef existing = nodeService.getChildByName(statsConfigFolder, ContentModel.ASSOC_CONTAINS, DEFAULT_STATS_CONFIG_NAME);
        if (existing != null) {
            QName currentType = nodeService.getType(existing);
            if (!STATS_CONFIG_TYPE.equals(currentType)) {
                nodeService.setType(existing, STATS_CONFIG_TYPE);
                logger.info("Specialised existing " + DEFAULT_STATS_CONFIG_NAME + " to type venzia:statsConfig");
            } else {
                logger.debug("Default stats config file already exists with correct type.");
            }
            return;
        }

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(DEFAULT_STATS_CONFIG_RESOURCE)) {
            if (input == null) {
                logger.warn("Could not find bootstrap resource: " + DEFAULT_STATS_CONFIG_RESOURCE);
                return;
            }

            FileInfo created = fileFolderService.create(statsConfigFolder, DEFAULT_STATS_CONFIG_NAME, STATS_CONFIG_TYPE);
            ContentWriter writer = fileFolderService.getWriter(created.getNodeRef());
            writer.setMimetype(MimetypeMap.MIMETYPE_JSON);
            writer.setEncoding("UTF-8");
            writer.putContent(input);
            logger.info("Bootstrapped " + DEFAULT_STATS_CONFIG_NAME + " into Data Dictionary/Stats Config as venzia:statsConfig");
        } catch (Exception e) {
            logger.error("Failed to bootstrap default stats config JSON", e);
        }
    }
}